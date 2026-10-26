package finance.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import finance.domain.DebitCredit;
import finance.domain.GlAccount;
import finance.domain.GlEntry;
import finance.domain.GlTransaction;
import finance.domain.Period;

/**
 * P5-F2-S1/S3 ledger use case: accounts, guarded postings (double-entry +
 * open period), one-way period close, and the trial balance over posted
 * transactions. One outbox event per posting and per period close.
 */
public class GlService {

    public static final String TRANSACTION_POSTED = "finance.transaction.posted.v1";
    public static final String PERIOD_CLOSED = "finance.period.closed.v1";

    /** One trial-balance row per account with activity (P5-F2-S3). */
    public record TrialBalanceRow(String glAccountId, String accountCode, String accountName,
            BigDecimal debitTotal, BigDecimal creditTotal, BigDecimal balance) {
    }

    public record TrialBalance(List<TrialBalanceRow> rows, BigDecimal totalDebits,
            BigDecimal totalCredits) {

        /** A trial balance that does not balance means the ledger is broken. */
        public boolean isBalanced() {
            return totalDebits.compareTo(totalCredits) == 0;
        }
    }

    /** P9-F1 statement slice: class-bucketed account totals for the SPA. */
    public record StatementRow(String glAccountId, String accountCode, String accountName,
            BigDecimal amount) {
    }

    public record BalanceSheet(List<StatementRow> assets, List<StatementRow> liabilities,
            List<StatementRow> equity, BigDecimal totalAssets,
            BigDecimal totalLiabilitiesAndEquity) {
    }

    public record IncomeStatement(List<StatementRow> income, List<StatementRow> expenses,
            BigDecimal netIncome) {
    }

    private final GlAccountStore accounts;
    private final GlTransactionStore transactions;
    private final PeriodStore periods;
    private final OutboxWriter outbox;
    private final FinanceSerializer serializer;

    public GlService(GlAccountStore accounts, GlTransactionStore transactions,
            PeriodStore periods, OutboxWriter outbox, FinanceSerializer serializer) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.periods = periods;
        this.outbox = outbox;
        this.serializer = serializer;
    }

    public Optional<GlTransaction> find(String acctgTransId) {
        return transactions.find(acctgTransId);
    }

    public Optional<Period> findPeriod(String periodId) {
        return periods.find(periodId);
    }

    public GlAccount createAccount(String glAccountId, String accountCode, String accountName,
            DebitCredit postBalance, String glAccountClassId) {
        GlAccount account = new GlAccount(glAccountId, accountCode, accountName, postBalance,
            glAccountClassId);
        accounts.save(account);
        return account;
    }

    public Optional<GlAccount> getAccount(String glAccountId) {
        return accounts.find(glAccountId);
    }

    /** P9-F1: chart of accounts for the SPA accounting area. */
    public List<GlAccount> allAccounts() {
        return accounts.all();
    }

    /** P9-F1: journal listing (posted + unposted) for the SPA accounting area. */
    public List<GlTransaction> allTransactions() {
        return transactions.all();
    }

    /**
     * P9-F1: balance sheet — assets vs liabilities + equity, bucketed by
     * glAccountClassId over the trial-balance rows. Balances when the ledger
     * does (double-entry implies assets = liabilities + equity + net income).
     */
    public BalanceSheet balanceSheet() {
        List<StatementRow> assets = new java.util.ArrayList<>();
        List<StatementRow> liabilities = new java.util.ArrayList<>();
        List<StatementRow> equity = new java.util.ArrayList<>();
        for (TrialBalanceRow row : trialBalance().rows()) {
            String cls = classOf(row.glAccountId());
            StatementRow statement = new StatementRow(row.glAccountId(), row.accountCode(),
                row.accountName(), signedBalance(row, cls));
            switch (cls) {
                case "ASSET" -> assets.add(statement);
                case "LIABILITY" -> liabilities.add(statement);
                case "EQUITY" -> equity.add(statement);
                default -> { }
            }
        }
        BigDecimal totalAssets = sum(assets);
        BigDecimal totalLiabilitiesAndEquity = sum(liabilities).add(sum(equity));
        return new BalanceSheet(assets, liabilities, equity, totalAssets,
            totalLiabilitiesAndEquity);
    }

    /** P9-F1: income statement — revenue minus expenses, net income included. */
    public IncomeStatement incomeStatement() {
        List<StatementRow> income = new java.util.ArrayList<>();
        List<StatementRow> expenses = new java.util.ArrayList<>();
        for (TrialBalanceRow row : trialBalance().rows()) {
            String cls = classOf(row.glAccountId());
            StatementRow statement = new StatementRow(row.glAccountId(), row.accountCode(),
                row.accountName(), signedBalance(row, cls));
            switch (cls) {
                case "REVENUE" -> income.add(statement);
                case "EXPENSE" -> expenses.add(statement);
                default -> { }
            }
        }
        return new IncomeStatement(income, expenses, sum(income).subtract(sum(expenses)));
    }

    private String classOf(String glAccountId) {
        String cls = accounts.find(glAccountId).map(GlAccount::glAccountClassId)
            .orElse("").toUpperCase();
        return "INCOME".equals(cls) ? "REVENUE" : cls;
    }

    /** Debit-natural classes report debit-credit; credit-natural the reverse. */
    private BigDecimal signedBalance(TrialBalanceRow row, String cls) {
        return switch (cls) {
            case "ASSET", "EXPENSE" -> row.debitTotal().subtract(row.creditTotal());
            default -> row.creditTotal().subtract(row.debitTotal());
        };
    }

    private BigDecimal sum(List<StatementRow> rows) {
        return rows.stream().map(StatementRow::amount).reduce(BigDecimal.ZERO,
            BigDecimal::add);
    }

    public Period createPeriod(String periodId, java.time.LocalDate from,
            java.time.LocalDate thru) {
        Period period = new Period(periodId, from, thru);
        periods.save(period);
        return period;
    }

    /** P5-F2-S1 AC: closed period blocks postings (guarded in the domain). */
    public Period closePeriod(String periodId) {
        Period period = periods.find(periodId)
            .orElseThrow(() -> new NoSuchElementException("no period " + periodId));
        period.close();
        periods.save(period);
        outbox.append(PERIOD_CLOSED, periodId, serializer.serializePeriodClosed(period));
        return period;
    }

    public GlTransaction postTransaction(String acctgTransId, String transType,
            String description, String periodId, List<GlEntry> entries) {
        Period period = periods.find(periodId)
            .orElseThrow(() -> new NoSuchElementException("no period " + periodId));
        for (GlEntry entry : entries) {
            if (accounts.find(entry.glAccountId()).isEmpty()) {
                throw new NoSuchElementException("no gl account " + entry.glAccountId());
            }
        }
        GlTransaction transaction = new GlTransaction(acctgTransId, transType, description,
            periodId, entries);
        transaction.post(period, Instant.now());
        transactions.save(transaction);
        outbox.append(TRANSACTION_POSTED, acctgTransId, serializer.serializeTransaction(transaction));
        return transaction;
    }

    /** The open period auto-posting targets; the earliest open one wins. */
    public String defaultOpenPeriodId() {
        return periods.allOpen().stream().map(Period::periodId).sorted().findFirst()
            .orElseThrow(() -> new NoSuchElementException("no open period"));
    }

    /**
     * P5-F2-S3: per-account debit/credit totals over posted transactions,
     * ordered by account code. Balanced == the double-entry invariant held.
     */
    public TrialBalance trialBalance() {
        Map<String, BigDecimal> debits = new LinkedHashMap<>();
        Map<String, BigDecimal> credits = new LinkedHashMap<>();
        for (GlTransaction transaction : transactions.findAllPosted()) {
            for (GlEntry entry : transaction.entries()) {
                Map<String, BigDecimal> side = entry.debitCredit() == DebitCredit.DEBIT
                    ? debits : credits;
                side.merge(entry.glAccountId(), entry.amount(), BigDecimal::add);
            }
        }
        java.util.Set<String> accountIds = new java.util.TreeSet<>();
        accountIds.addAll(debits.keySet());
        accountIds.addAll(credits.keySet());
        List<TrialBalanceRow> rows = new java.util.ArrayList<>();
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        for (String accountId : accountIds) {
            GlAccount account = accounts.find(accountId).orElseThrow();
            BigDecimal debit = debits.getOrDefault(accountId, BigDecimal.ZERO);
            BigDecimal credit = credits.getOrDefault(accountId, BigDecimal.ZERO);
            rows.add(new TrialBalanceRow(accountId, account.accountCode(), account.accountName(),
                debit, credit, debit.subtract(credit)));
            totalDebits = totalDebits.add(debit);
            totalCredits = totalCredits.add(credit);
        }
        return new TrialBalance(rows, totalDebits, totalCredits);
    }
}
