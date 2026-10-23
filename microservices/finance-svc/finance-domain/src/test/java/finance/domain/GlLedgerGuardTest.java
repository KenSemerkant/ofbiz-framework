package finance.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * P5-F2-S1 guard tests: the double-entry invariant and the closed-period
 * block are the reason finance-svc exists — a ledger that can post
 * unbalanced or into a closed period is wrong by definition.
 */
class GlLedgerGuardTest {

    private static final Period OPEN = new Period("P-2026", LocalDate.parse("2026-01-01"),
        LocalDate.parse("2026-12-31"));

    private static GlTransaction balanced() {
        return new GlTransaction("TT-1", "SALES_INVOICE", "inv", "P-2026", List.of(
            GlEntry.debit("1200", new BigDecimal("22.00")),
            GlEntry.credit("4000", new BigDecimal("22.00"))));
    }

    @Test
    void balancedTransactionPostsOnceAndFreezes() {
        GlTransaction transaction = balanced();
        transaction.post(OPEN, Instant.now());
        assertTrue(transaction.isPosted());
        assertEquals(0, transaction.total(DebitCredit.DEBIT)
            .compareTo(new BigDecimal("22.00")));
        assertEquals(0, transaction.total(DebitCredit.CREDIT)
            .compareTo(new BigDecimal("22.00")));
        assertThrows(AlreadyPostedException.class,
            () -> transaction.post(OPEN, Instant.now()));
        assertEquals("TT-1", transaction.acctgTransId());
        assertEquals("SALES_INVOICE", transaction.transType());
        assertEquals("inv", transaction.description());
        assertEquals("P-2026", transaction.periodId());
        assertEquals(2, transaction.entries().size());
        assertTrue(transaction.postedAt() != null);
    }

    @Test
    void unbalancedTransactionRefusesToPost() {
        GlTransaction transaction = new GlTransaction("TT-2", "SALES_INVOICE", "inv",
            "P-2026", List.of(GlEntry.debit("1200", new BigDecimal("22.00")),
                GlEntry.credit("4000", new BigDecimal("21.99"))));
        assertThrows(UnbalancedTransactionException.class,
            () -> transaction.post(OPEN, Instant.now()));
        assertEquals(false, transaction.isPosted());
    }

    @Test
    void closedPeriodBlocksPosting() {
        Period closed = Period.rehydrate("P-2025", LocalDate.parse("2025-01-01"),
            LocalDate.parse("2025-12-31"), true);
        GlTransaction transaction = new GlTransaction("TT-3", "SALES_INVOICE", "inv",
            "P-2025", List.of(GlEntry.debit("1200", BigDecimal.TEN),
                GlEntry.credit("4000", BigDecimal.TEN)));
        assertThrows(ClosedPeriodException.class, () -> transaction.post(closed,
            Instant.now()));
    }

    @Test
    void constructionGuards() {
        assertThrows(IllegalArgumentException.class,
            () -> new GlTransaction("", "T", "d", "P", List.of(GlEntry.debit("1",
                BigDecimal.ONE), GlEntry.credit("2", BigDecimal.ONE))));
        assertThrows(IllegalArgumentException.class,
            () -> new GlTransaction("TT", "T", "d", "P", List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> GlEntry.debit(" ", BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
            () -> GlEntry.credit("1", BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
            () -> new GlEntry("1", BigDecimal.ONE, null));
    }

    @Test
    void periodGuardsAndOneWayClose() {
        assertThrows(IllegalArgumentException.class,
            () -> new Period("P", LocalDate.parse("2026-12-31"),
                LocalDate.parse("2026-01-01")));
        assertThrows(IllegalArgumentException.class,
            () -> new Period(" ", LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-12-31")));
        Period period = new Period("P", LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-12-31"));
        assertEquals(false, period.isClosed());
        period.close();
        assertTrue(period.isClosed());
        assertEquals("P", period.periodId());
        assertEquals(LocalDate.parse("2026-01-01"), period.fromDate());
        assertEquals(LocalDate.parse("2026-12-31"), period.thruDate());
    }

    @Test
    void postingRuleConstructionGuards() {
        PostingRule rule = new PostingRule("R-1", "billing.invoice.issued.v1", "1200", "4000",
            true);
        assertEquals("R-1", rule.ruleId());
        assertTrue(rule.enabled());
        assertThrows(IllegalArgumentException.class,
            () -> new PostingRule("", "t", "d", "c", true));
        assertThrows(IllegalArgumentException.class,
            () -> new PostingRule("R", " ", "d", "c", true));
        assertThrows(IllegalArgumentException.class,
            () -> new PostingRule("R", "t", " ", "c", true));
        assertThrows(IllegalArgumentException.class,
            () -> new PostingRule("R", "t", "d", " ", true));
    }

    @Test
    void accountAndRehydrateGuards() {
        GlAccount account = new GlAccount("1000", "1200", "Accounts Receivable",
            DebitCredit.DEBIT, "ASSET");
        assertEquals("1200", account.accountCode());
        assertEquals("Accounts Receivable", account.accountName());
        assertEquals(DebitCredit.DEBIT, account.postBalance());
        assertEquals("ASSET", account.glAccountClassId());
        assertEquals("1000", account.glAccountId());
        assertThrows(IllegalArgumentException.class,
            () -> new GlAccount(" ", "1200", "n", DebitCredit.DEBIT, null));
        assertThrows(IllegalArgumentException.class,
            () -> new GlAccount("1000", " ", "n", DebitCredit.DEBIT, null));
        assertThrows(IllegalArgumentException.class,
            () -> new GlAccount("1000", "1200", " ", DebitCredit.DEBIT, null));
        assertThrows(IllegalArgumentException.class,
            () -> new GlAccount("1000", "1200", "n", null, null));
        GlTransaction rehydrated = GlTransaction.rehydrate("TT-R", "T", "d", "P", List.of(
            GlEntry.debit("1", BigDecimal.ONE), GlEntry.credit("2", BigDecimal.ONE)), true,
            Instant.EPOCH);
        assertTrue(rehydrated.isPosted());
    }
}
