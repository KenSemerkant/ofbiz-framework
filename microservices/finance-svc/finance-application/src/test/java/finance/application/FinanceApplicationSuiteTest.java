package finance.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import finance.domain.DebitCredit;
import finance.domain.GlEntry;
import finance.domain.GlTransaction;
import finance.domain.Period;
import finance.domain.PostingRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * P5-F2 use-case suite: double-entry posting, closed-period block, the
 * auto-posting consumer rules (configurable, park-on-failure, idempotent)
 * and the trial balance the CG-5 checker compares against the monolith.
 */
class FinanceApplicationSuiteTest {

    private final FakeStores stores = new FakeStores();
    private final List<String> outbox = new ArrayList<>();
    private final GlService gl;
    private final AutoPostingService autoPosting;

    FinanceApplicationSuiteTest() {
        FinanceSerializer serializer = new FinanceSerializer() {
            @Override
            public String serializeTransaction(GlTransaction t) {
                return "trans:" + t.acctgTransId();
            }

            @Override
            public String serializePeriodClosed(Period p) {
                return "period:" + p.periodId();
            }
        };
        gl = new GlService(stores.accounts, stores.transactions, stores.periods,
            (type, key, payload) -> outbox.add(type + ":" + key), serializer);
        autoPosting = new AutoPostingService(gl, stores.rules, stores.parked);
        gl.createAccount("1200", "1200", "Accounts Receivable", DebitCredit.DEBIT, "ASSET");
        gl.createAccount("4000", "4000", "Sales Revenue", DebitCredit.CREDIT, "REVENUE");
        gl.createAccount("1100", "1100", "Cash", DebitCredit.DEBIT, "ASSET");
        gl.createPeriod("P-2026", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));
    }

    @Test
    void balancedPostingLandsAndEmitsEvent() {
        GlTransaction posted = gl.postTransaction("TT-1", "MANUAL", "test", "P-2026", List.of(
            GlEntry.debit("1200", new BigDecimal("22.00")),
            GlEntry.credit("4000", new BigDecimal("22.00"))));
        assertTrue(posted.isPosted());
        assertTrue(outbox.contains("finance.transaction.posted.v1:TT-1"));
        assertEquals(posted.acctgTransId(), gl.find("TT-1").orElseThrow().acctgTransId());
    }

    @Test
    void unbalancedPostingRefusedWithTypedError() {
        assertThrows(finance.domain.UnbalancedTransactionException.class,
            () -> gl.postTransaction("TT-2", "MANUAL", "test", "P-2026", List.of(
                GlEntry.debit("1200", new BigDecimal("22.00")),
                GlEntry.credit("4000", new BigDecimal("21.99")))));
        assertTrue(gl.find("TT-2").isEmpty());
    }

    @Test
    void closedPeriodBlocksPostings() {
        gl.createPeriod("P-2025", LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"));
        Period closed = gl.closePeriod("P-2025");
        assertTrue(closed.isClosed());
        assertTrue(outbox.contains("finance.period.closed.v1:P-2025"));
        assertThrows(finance.domain.ClosedPeriodException.class,
            () -> gl.postTransaction("TT-3", "MANUAL", "late", "P-2025", List.of(
                GlEntry.debit("1200", BigDecimal.ONE),
                GlEntry.credit("4000", BigDecimal.ONE))));
    }

    @Test
    void autoPostingFollowsConfiguredRules() {
        stores.rules.save(new PostingRule("R-1", "billing.invoice.issued.v1", "1200", "4000",
            true));
        stores.rules.save(new PostingRule("R-2", "billing.invoice.settled.v1", "1100", "1200",
            true));
        String transId = autoPosting.onBillingEvent("billing.invoice.issued.v1", "INV-1",
            new BigDecimal("22.00"), "USD", "invoice INV-1");
        assertNotNull(transId);
        assertEquals(transId, autoPosting.onBillingEvent("billing.invoice.issued.v1", "INV-1",
            new BigDecimal("22.00"), "USD", "replay"), "redelivery is a no-op");
        autoPosting.onBillingEvent("billing.invoice.settled.v1", "INV-1",
            new BigDecimal("22.00"), "USD", "settle INV-1");
        assertTrue(outbox.contains("finance.transaction.posted.v1:ATT-INV-1-issued"));
        assertTrue(outbox.contains("finance.transaction.posted.v1:ATT-INV-1-settled"));
        assertEquals(0, stores.parked.parkedReasons().size());
    }

    @Test
    void missingRuleIsSkippedAndNeverParked() {
        String result = autoPosting.onBillingEvent("billing.payment.captured.v1", "PAY-1",
            BigDecimal.TEN, "USD", "no rule for this");
        assertEquals(null, result);
        assertEquals(0, stores.parked.parkedReasons().size());
    }

    @Test
    void guardFailureParksForReviewNeverDrops() {
        stores.rules.save(new PostingRule("R-9", "billing.invoice.issued.v1", "1200", "9999",
            true));
        String result = autoPosting.onBillingEvent("billing.invoice.issued.v1", "INV-9",
            BigDecimal.TEN, "USD", "broken rule");
        assertEquals(null, result);
        assertEquals(1, stores.parked.parkedReasons().size());
        assertTrue(stores.parked.parkedReasons().get(0).contains("9999"));
    }

    @Test
    void trialBalanceBalancesAndRowsByAccount() {
        stores.rules.save(new PostingRule("R-1", "billing.invoice.issued.v1", "1200", "4000",
            true));
        stores.rules.save(new PostingRule("R-2", "billing.invoice.settled.v1", "1100", "1200",
            true));
        autoPosting.onBillingEvent("billing.invoice.issued.v1", "INV-1",
            new BigDecimal("22.00"), "USD", "inv");
        autoPosting.onBillingEvent("billing.invoice.settled.v1", "INV-1",
            new BigDecimal("22.00"), "USD", "settle");
        GlService.TrialBalance tb = gl.trialBalance();
        assertTrue(tb.isBalanced());
        assertEquals(0, tb.totalDebits().compareTo(new BigDecimal("44.00")));
        assertEquals(3, tb.rows().size());
        assertEquals("1100", tb.rows().get(0).glAccountId());
        GlService.TrialBalanceRow ar = tb.rows().stream()
            .filter(r -> r.glAccountId().equals("1200")).findFirst().orElseThrow();
        assertEquals(0, ar.debitTotal().compareTo(new BigDecimal("22.00")));
        assertEquals(0, ar.creditTotal().compareTo(new BigDecimal("22.00")));
        assertEquals(0, ar.balance().compareTo(BigDecimal.ZERO));
        assertEquals("1200", ar.accountCode());
        assertEquals("Accounts Receivable", ar.accountName());
    }

    @Test
    void missingPeriodOrAccountOnManualPostingThrows() {
        assertThrows(java.util.NoSuchElementException.class,
            () -> gl.postTransaction("TT-4", "MANUAL", "d", "P-NONE", List.of(
                GlEntry.debit("1200", BigDecimal.ONE),
                GlEntry.credit("4000", BigDecimal.ONE))));
        assertThrows(java.util.NoSuchElementException.class,
            () -> gl.postTransaction("TT-5", "MANUAL", "d", "P-2026", List.of(
                GlEntry.debit("9999", BigDecimal.ONE),
                GlEntry.credit("4000", BigDecimal.ONE))));
        assertThrows(java.util.NoSuchElementException.class, () -> gl.closePeriod("P-NONE"));
        assertTrue(gl.getAccount("1200").isPresent());
    }

    /** In-memory fakes for every store port. */
    private static final class FakeStores {
        final FakeAccountStore accounts = new FakeAccountStore();
        final FakeTransactionStore transactions = new FakeTransactionStore();
        final FakePeriodStore periods = new FakePeriodStore();
        final FakeRuleStore rules = new FakeRuleStore();
        final FakeParkedStore parked = new FakeParkedStore();
    }

    static final class FakeAccountStore implements GlAccountStore {
        final List<finance.domain.GlAccount> rows = new ArrayList<>();

        @Override
        public Optional<finance.domain.GlAccount> find(String glAccountId) {
            return rows.stream().filter(a -> a.glAccountId().equals(glAccountId)).findFirst();
        }

        @Override
        public List<finance.domain.GlAccount> all() {
            return rows.stream()
                .sorted(java.util.Comparator.comparing(finance.domain.GlAccount::accountCode))
                .toList();
        }

        @Override
        public void save(finance.domain.GlAccount account) {
            rows.removeIf(a -> a.glAccountId().equals(account.glAccountId()));
            rows.add(account);
        }
    }

    static final class FakeTransactionStore implements GlTransactionStore {
        final List<GlTransaction> rows = new ArrayList<>();

        @Override
        public Optional<GlTransaction> find(String acctgTransId) {
            return rows.stream().filter(t -> t.acctgTransId().equals(acctgTransId))
                .findFirst();
        }

        @Override
        public List<GlTransaction> all() {
            return rows.stream()
                .sorted(java.util.Comparator.comparing(GlTransaction::acctgTransId)).toList();
        }

        @Override
        public void save(GlTransaction transaction) {
            rows.removeIf(t -> t.acctgTransId().equals(transaction.acctgTransId()));
            rows.add(transaction);
        }

        @Override
        public List<GlTransaction> findAllPosted() {
            return rows.stream().filter(GlTransaction::isPosted).toList();
        }
    }

    static final class FakePeriodStore implements PeriodStore {
        final List<Period> rows = new ArrayList<>();

        @Override
        public Optional<Period> find(String periodId) {
            return rows.stream().filter(p -> p.periodId().equals(periodId)).findFirst();
        }

        @Override
        public void save(Period period) {
            rows.removeIf(p -> p.periodId().equals(period.periodId()));
            rows.add(period);
        }

        @Override
        public List<Period> allOpen() {
            return rows.stream().filter(p -> !p.isClosed()).toList();
        }
    }

    static final class FakeRuleStore implements PostingRuleStore {
        final List<PostingRule> rows = new ArrayList<>();

        @Override
        public Optional<PostingRule> findByEventType(String eventType) {
            return rows.stream().filter(r -> r.eventType().equals(eventType)).findFirst();
        }

        @Override
        public void save(PostingRule rule) {
            rows.removeIf(r -> r.ruleId().equals(rule.ruleId()));
            rows.add(rule);
        }

        @Override
        public List<PostingRule> all() {
            return List.copyOf(rows);
        }
    }

    static final class FakeParkedStore implements ParkedPostingStore {
        final List<String> reasons = new ArrayList<>();

        @Override
        public void park(String eventType, String aggregateKey, String reason,
                String payloadJson) {
            reasons.add("%s %s: %s".formatted(eventType, aggregateKey, reason));
        }

        @Override
        public List<String> parkedReasons() {
            return List.copyOf(reasons);
        }
    }
}
