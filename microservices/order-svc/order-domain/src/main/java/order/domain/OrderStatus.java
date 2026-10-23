package order.domain;

import java.util.Map;
import java.util.Set;

/**
 * P4-F1-S1 status machine (enum-guarded): every legal transition is declared
 * here and only here — the aggregate consults this table, so an illegal
 * transition can never bypass the guard. Mirrors the OFBiz ORDER_STATUS
 * flow used by the staging e2e (created → approved → completed / cancelled).
 */
public enum OrderStatus {

    CREATED, APPROVED, COMPLETED, CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
        CREATED, Set.of(APPROVED, CANCELLED),
        APPROVED, Set.of(COMPLETED, CANCELLED),
        COMPLETED, Set.of(),
        CANCELLED, Set.of());

    /** @return true when from → to is a legal lifecycle transition. */
    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /** @throws IllegalOrderTransitionException when from → to is illegal. */
    public static void requireTransition(OrderStatus from, OrderStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalOrderTransitionException(from, to);
        }
    }
}
