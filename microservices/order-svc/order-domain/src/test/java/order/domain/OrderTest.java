package order.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * P4-F1-S1 AC: illegal transitions rejected with the typed error, history
 * appended per transition; P4-F1-S2: line snapshot is immutable.
 */
class OrderTest {

    private static Order newOrder() {
        return new Order("ORDER-1", "CUST-1", "PROMO-42",
            List.of(new OrderLine("00001", "PROD-1", "WH", new BigDecimal("2"),
                new BigDecimal("9.99"), "USD")),
            Instant.parse("2026-10-22T10:00:00Z"));
    }

    @Test
    void legalLifecycleCreatedApprovedCompleted() {
        Order order = newOrder();
        order.approve(Instant.parse("2026-10-22T11:00:00Z"));
        order.complete(Instant.parse("2026-10-22T12:00:00Z"));
        assertEquals(OrderStatus.COMPLETED, order.status());
        assertEquals(List.of("CREATED", "APPROVED", "COMPLETED"),
            order.history().stream().map(change -> change.status().name()).toList());
    }

    @Test
    void illegalTransitionThrowsTypedError() {
        Order order = newOrder();
        IllegalOrderTransitionException error = assertThrows(
            IllegalOrderTransitionException.class,
            () -> order.complete(Instant.now()));
        assertEquals(OrderStatus.CREATED, error.from());
        assertEquals(OrderStatus.COMPLETED, error.to());
        // cancel then try to approve: terminal states reject everything
        order.cancel(Instant.now(), "buyer remorse");
        assertThrows(IllegalOrderTransitionException.class,
            () -> order.approve(Instant.now()));
    }

    @Test
    void historyAppendsPerTransitionWithTimestamps() {
        Order order = newOrder();
        Instant at = Instant.parse("2026-10-22T11:30:00Z");
        order.approve(at);
        OrderStatusChange change = order.history().get(order.history().size() - 1);
        assertEquals(OrderStatus.APPROVED, change.status());
        assertEquals(at, change.changedAt());
    }

    @Test
    void linesAreTheImmutablePriceSnapshot() {
        Order order = newOrder();
        assertThrows(Exception.class, () -> order.lines().add(new OrderLine("00002", "PROD-2",
            "WH", BigDecimal.ONE, BigDecimal.TEN, "USD")));
        assertEquals(new BigDecimal("19.98"), order.grandTotal());
    }

    @Test
    void constructorGuards() {
        List<OrderLine> one = newOrder().lines();
        assertThrows(IllegalArgumentException.class, () -> new Order("", "CUST", null, one,
            Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new Order("ORDER-X", " ", null, one,
            Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new Order("ORDER-X", "CUST", null,
            null, Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new Order("ORDER-X", "CUST", null,
            List.of(), Instant.now()));
    }

    @Test
    void rehydrateTrustsPersistedStateAndAccessors() {
        List<OrderStatusChange> history = List.of(
            new OrderStatusChange(OrderStatus.CREATED, Instant.now(), "placed"),
            new OrderStatusChange(OrderStatus.CANCELLED, Instant.now(), "note"));
        Order order = Order.rehydrate("ORDER-R", "CUST-1", "PROMO-1",
            newOrder().lines(), OrderStatus.CANCELLED, history);
        assertEquals("ORDER-R", order.orderId());
        assertEquals("CUST-1", order.customerId());
        assertEquals("PROMO-1", order.trackingCode());
        assertEquals(OrderStatus.CANCELLED, order.status());
        assertEquals(history, order.history());

        // cancel with a null note still appends a history entry
        Order fresh = newOrder();
        fresh.cancel(Instant.now(), null);
        assertEquals("cancelled", fresh.history().get(fresh.history().size() - 1).note());
    }
}
