package order.domain;

/**
 * P4-F1-S1 typed error: the API layer maps this to 409 Conflict so callers
 * can distinguish "wrong state" (retry after approve) from "bad request".
 */
public class IllegalOrderTransitionException extends RuntimeException {

    private final OrderStatus from;
    private final OrderStatus to;

    public IllegalOrderTransitionException(OrderStatus from, OrderStatus to) {
        super("illegal order transition " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public OrderStatus from() {
        return from;
    }

    public OrderStatus to() {
        return to;
    }
}
