package order.application;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import order.domain.Order;

/**
 * P4-F1-S1 lifecycle use case: guarded transitions, history appended by the
 * aggregate, one outbox event per state change (approve/complete/cancel).
 */
public class OrderLifecycleService {

    public static final String ORDER_APPROVED = "order.order.approved.v1";
    public static final String ORDER_COMPLETED = "order.order.completed.v1";
    public static final String ORDER_CANCELLED = "order.order.cancelled.v1";

    private final OrderStore orders;
    private final OutboxWriter outbox;
    private final OrderSerializer serializer;

    public OrderLifecycleService(OrderStore orders, OutboxWriter outbox,
            OrderSerializer serializer) {
        this.orders = orders;
        this.outbox = outbox;
        this.serializer = serializer;
    }

    /** P9-F3: order listing for the SPA order area (null filter = match all). */
    public java.util.List<Order> search(String customerId, String status) {
        return orders.all().stream()
            .filter(o -> customerId == null || customerId.equals(o.customerId()))
            .filter(o -> status == null || status.equals(o.status().name()))
            .sorted(java.util.Comparator.comparing(Order::orderId))
            .toList();
    }

    public Optional<Order> get(String orderId) {
        return orders.find(orderId);
    }

    public Order approve(String orderId) {
        return transition(orderId, (order, at) -> order.approve(at), ORDER_APPROVED,
            "approved");
    }

    public Order complete(String orderId) {
        return transition(orderId, (order, at) -> order.complete(at), ORDER_COMPLETED,
            "completed");
    }

    public Order cancel(String orderId, String note) {
        return transition(orderId, (order, at) -> order.cancel(at, note),
            ORDER_CANCELLED, note);
    }

    @FunctionalInterface
    private interface Transition {
        void apply(Order order, Instant at);
    }

    private Order transition(String orderId, Transition action, String eventType, String note) {
        Order order = load(orderId);
        action.apply(order, Instant.now());
        orders.save(order);
        outbox.append(eventType, orderId, serializer.serializeStatusChange(order, note));
        return order;
    }

    private Order load(String orderId) {
        return orders.find(orderId)
            .orElseThrow(() -> new NoSuchElementException("no order " + orderId));
    }
}
