package order.application;

import java.time.Instant;

/** Persistence port for marketing attributions (P4-F2-S2). */
public interface AttributionStore {

    record Attribution(String orderId, String customerId, String trackingCode, Instant attributedAt) {
    }

    void record(Attribution attribution);

    long countByTrackingCode(String trackingCode);
}
