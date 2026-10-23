package billing.domain;

/** Typed guard violation (P5-F1-S1): surfaces as a 409 at the REST edge. */
public final class IllegalInvoiceTransitionException extends RuntimeException {

    public IllegalInvoiceTransitionException(InvoiceStatus from, InvoiceStatus to) {
        super("illegal invoice transition %s -> %s".formatted(from, to));
    }
}
