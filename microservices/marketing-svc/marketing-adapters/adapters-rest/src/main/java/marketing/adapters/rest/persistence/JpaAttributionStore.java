package marketing.adapters.rest.persistence;

import java.util.List;
import marketing.application.AttributionStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * JPA adapter for the AttributionStore port. The order_id primary key
 * makes a replayed insert a no-op — the second idempotency line behind
 * processed_events (ADR-009).
 */
@Repository
public class JpaAttributionStore implements AttributionStore {

    private final AttributionRepository attributions;

    public JpaAttributionStore(AttributionRepository attributions) {
        this.attributions = attributions;
    }

    @Override
    public void record(Attribution attribution) {
        try {
            attributions.save(new AttributionEntity(attribution.orderId(),
                attribution.trackingCode(), attribution.orderTotal(),
                attribution.occurredAt()));
        } catch (DataIntegrityViolationException e) {
            // duplicate order_id — the event was already applied
        }
    }

    @Override
    public List<Attribution> findAll() {
        return attributions.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Attribution> findByTrackingCode(String trackingCode) {
        return attributions.findByTrackingCodeOrderByOrderIdAsc(trackingCode).stream()
            .map(this::toDomain).toList();
    }

    private Attribution toDomain(AttributionEntity entity) {
        return new Attribution(entity.getOrderId(), entity.getTrackingCode(),
            entity.getOrderTotal(), entity.getOccurredAt());
    }
}
