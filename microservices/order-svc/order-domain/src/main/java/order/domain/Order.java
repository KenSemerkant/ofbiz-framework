package order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Order aggregate root (P4-F1-S1): lines are the immutable price snapshot
 * captured at placement; every lifecycle transition is guarded by
 * {@link OrderStatus} and appends to the history. The application layer
 * emits the matching domain event per state change.
 */
public final class Order {

    private final String orderId;
    private final String customerId;
    private final String trackingCode;
    private final List<OrderLine> lines;
    private OrderStatus status;
    private final List<OrderStatusChange> history = new ArrayList<>();

    public Order(String orderId, String customerId, String trackingCode,
            List<OrderLine> lines, Instant placedAt) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be blank");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("an order needs at least one line");
        }
        this.orderId = orderId;
        this.customerId = customerId;
        this.trackingCode = trackingCode;
        this.lines = List.copyOf(lines);
        this.status = OrderStatus.CREATED;
        this.history.add(new OrderStatusChange(OrderStatus.CREATED, placedAt, "placed"));
    }

    /** @throws IllegalOrderTransitionException on a guarded violation. */
    public void approve(Instant at) {
        transition(OrderStatus.APPROVED, at, "approved");
    }

    public void complete(Instant at) {
        transition(OrderStatus.COMPLETED, at, "completed");
    }

    public void cancel(Instant at, String note) {
        transition(OrderStatus.CANCELLED, at, note == null ? "cancelled" : note);
    }

    private void transition(OrderStatus to, Instant at, String note) {
        OrderStatus.requireTransition(status, to);
        status = to;
        history.add(new OrderStatusChange(to, at, note));
    }

    /** Rehydration factory: the persisted status/history are trusted. */
    public static Order rehydrate(String orderId, String customerId, String trackingCode,
            List<OrderLine> lines, OrderStatus status, List<OrderStatusChange> history) {
        Order order = new Order(orderId, customerId, trackingCode, lines,
            history.isEmpty() ? Instant.EPOCH : history.get(0).changedAt());
        order.status = status;
        order.history.clear();
        order.history.addAll(history);
        return order;
    }

    /** Grand total over the immutable snapshot lines. */
    public BigDecimal grandTotal() {
        return lines.stream().map(OrderLine::lineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public String orderId() {
        return orderId;
    }

    public String customerId() {
        return customerId;
    }

    /** Marketing tracking code (P4-F2-S2); null when the order carries none. */
    public String trackingCode() {
        return trackingCode;
    }

    public List<OrderLine> lines() {
        return lines;
    }

    public OrderStatus status() {
        return status;
    }

    public List<OrderStatusChange> history() {
        return List.copyOf(history);
    }
}
