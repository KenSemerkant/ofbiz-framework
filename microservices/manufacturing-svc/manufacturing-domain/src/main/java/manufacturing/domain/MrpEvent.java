package manufacturing.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * P6-T1 MRP ledger row (monolith MrpEvent): one supply or demand fact for
 * a product, appended by the event consumers. mrp_events.event_id is the
 * primary key — replays collide rather than double-count.
 */
public record MrpEvent(String eventId, String productId, MrpEventType eventType,
        BigDecimal quantity, Instant occurredAt) {

    public MrpEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("eventType must not be null");
        }
        if (quantity == null || quantity.signum() == 0) {
            throw new IllegalArgumentException("quantity must not be zero");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
    }
}
