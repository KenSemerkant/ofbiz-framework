package marketing.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Persistence port for order attributions. The unique order_id makes a
 * replayed order.order.placed.v1 a no-op even without processed_events.
 */
public interface AttributionStore {

    record Attribution(String orderId, String trackingCode, BigDecimal orderTotal,
            Instant occurredAt) {
    }

    /** Insert-only; a duplicate orderId is silently skipped (idempotency). */
    void record(Attribution attribution);

    List<Attribution> findAll();

    List<Attribution> findByTrackingCode(String trackingCode);
}
