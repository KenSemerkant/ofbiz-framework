package billing.domain;

import java.util.Map;
import java.util.Set;

/**
 * P5-F1-S2 payment status machine: authorize → capture is the happy path;
 * an authorization can be voided; anything pending can fail. CAPTURED,
 * VOIDED and FAILED are terminal.
 */
public enum PaymentStatus {

    PENDING, AUTHORIZED, CAPTURED, VOIDED, FAILED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITIONS = Map.of(
        PENDING, Set.of(AUTHORIZED, FAILED, VOIDED),
        AUTHORIZED, Set.of(CAPTURED, VOIDED, FAILED),
        CAPTURED, Set.of(),
        VOIDED, Set.of(),
        FAILED, Set.of());

    /** @return true when from → to is a legal lifecycle transition. */
    public static boolean canTransition(PaymentStatus from, PaymentStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /** @throws IllegalPaymentTransitionException when from → to is illegal. */
    public static void requireTransition(PaymentStatus from, PaymentStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalPaymentTransitionException(from, to);
        }
    }
}
