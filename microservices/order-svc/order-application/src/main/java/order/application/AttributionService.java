package order.application;

import java.time.Instant;

/**
 * P4-F2-S2: records tracking-code attribution for placed orders (the
 * marketing-svc attribution report of P6-T5 joins on these rows later).
 */
public class AttributionService {

    private final AttributionStore attributions;

    public AttributionService(AttributionStore attributions) {
        this.attributions = attributions;
    }

    public void onOrderPlaced(String orderId, String customerId, String trackingCode,
            Instant placedAt) {
        if (trackingCode == null || trackingCode.isBlank()) {
            return; // no code on the order — nothing to attribute
        }
        attributions.record(new AttributionStore.Attribution(orderId, customerId, trackingCode,
            placedAt == null ? Instant.now() : placedAt));
    }

    public long countByTrackingCode(String trackingCode) {
        return attributions.countByTrackingCode(trackingCode);
    }
}
