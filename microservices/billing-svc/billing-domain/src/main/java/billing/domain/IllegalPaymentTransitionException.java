package billing.domain;

/** Typed guard violation (P5-F1-S2): surfaces as a 409 at the REST edge. */
public final class IllegalPaymentTransitionException extends RuntimeException {

    public IllegalPaymentTransitionException(PaymentStatus from, PaymentStatus to) {
        super("illegal payment transition %s -> %s".formatted(from, to));
    }
}
