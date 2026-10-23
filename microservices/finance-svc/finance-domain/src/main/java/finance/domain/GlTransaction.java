package finance.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * P5-F2-S1 journal transaction (AcctgTrans): a set of entries that posts
 * atomically — at least two entries and debits == credits, or the post is
 * refused. Entries are frozen at posting time (immutable journal).
 */
public final class GlTransaction {

    private final String acctgTransId;
    private final String transType;
    private final String description;
    private final String periodId;
    private final List<GlEntry> entries;
    private boolean posted;
    private Instant postedAt;

    public GlTransaction(String acctgTransId, String transType, String description,
            String periodId, List<GlEntry> entries) {
        if (acctgTransId == null || acctgTransId.isBlank()) {
            throw new IllegalArgumentException("acctgTransId must not be blank");
        }
        if (transType == null || transType.isBlank()) {
            throw new IllegalArgumentException("transType must not be blank");
        }
        if (periodId == null || periodId.isBlank()) {
            throw new IllegalArgumentException("periodId must not be blank");
        }
        if (entries == null || entries.size() < 2) {
            throw new IllegalArgumentException("a journal entry needs at least two legs");
        }
        this.acctgTransId = acctgTransId;
        this.transType = transType;
        this.description = description;
        this.periodId = periodId;
        this.entries = List.copyOf(entries);
    }

    /**
     * @throws AlreadyPostedException    on re-post
     * @throws ClosedPeriodException     when the period is closed
     * @throws UnbalancedTransactionException when debits != credits
     */
    public void post(Period period, Instant at) {
        if (posted) {
            throw new AlreadyPostedException(acctgTransId);
        }
        if (period == null || period.isClosed()) {
            throw new ClosedPeriodException(periodId);
        }
        BigDecimal debits = total(DebitCredit.DEBIT);
        BigDecimal credits = total(DebitCredit.CREDIT);
        if (debits.compareTo(credits) != 0) {
            throw new UnbalancedTransactionException(entries, debits, credits);
        }
        posted = true;
        postedAt = at;
    }

    public BigDecimal total(DebitCredit side) {
        return entries.stream().filter(e -> e.debitCredit() == side)
            .map(GlEntry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Rehydration factory: posted transactions are immutable, trusted state. */
    public static GlTransaction rehydrate(String acctgTransId, String transType,
            String description, String periodId, List<GlEntry> entries, boolean posted,
            Instant postedAt) {
        GlTransaction transaction = new GlTransaction(acctgTransId, transType, description,
            periodId, entries);
        transaction.posted = posted;
        transaction.postedAt = postedAt;
        return transaction;
    }

    public String acctgTransId() {
        return acctgTransId;
    }

    public String transType() {
        return transType;
    }

    public String description() {
        return description;
    }

    public String periodId() {
        return periodId;
    }

    public List<GlEntry> entries() {
        return entries;
    }

    public boolean isPosted() {
        return posted;
    }

    public Instant postedAt() {
        return postedAt;
    }
}
