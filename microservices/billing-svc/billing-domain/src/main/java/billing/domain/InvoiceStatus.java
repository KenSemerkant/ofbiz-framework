package billing.domain;

import java.util.Map;
import java.util.Set;

/**
 * P5-F1-S1 status machine (enum-guarded): every legal transition is declared
 * here and only here — the aggregate consults this table, so an illegal
 * transition can never bypass the guard. Mirrors the OFBiz INVOICE_STATUS
 * flow (in-process → ready → paid / cancelled / written-off).
 */
public enum InvoiceStatus {

    DRAFT, ISSUED, SETTLED, CANCELLED, WRITTEN_OFF;

    private static final Map<InvoiceStatus, Set<InvoiceStatus>> TRANSITIONS = Map.of(
        DRAFT, Set.of(ISSUED, CANCELLED),
        ISSUED, Set.of(SETTLED, CANCELLED, WRITTEN_OFF),
        SETTLED, Set.of(),
        CANCELLED, Set.of(),
        WRITTEN_OFF, Set.of());

    /** @return true when from → to is a legal lifecycle transition. */
    public static boolean canTransition(InvoiceStatus from, InvoiceStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /** @throws IllegalInvoiceTransitionException when from → to is illegal. */
    public static void requireTransition(InvoiceStatus from, InvoiceStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalInvoiceTransitionException(from, to);
        }
    }
}
