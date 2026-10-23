package order.domain;

import java.time.Instant;

/** One row of the append-only status history (P4-F1-S1 AC). */
public record OrderStatusChange(OrderStatus status, Instant changedAt, String note) {
}
