package order.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import order.application.AttributionStore;
import order.application.ProcessedEventStore;

/** processed_events + order_attributions: tiny key/value stores, one file. */
public final class SmallStores {

    @Entity
    @Table(name = "processed_events")
    public static class ProcessedEventEntity {

        @Id
        @Column(name = "event_id")
        private String eventId;

        @Column(name = "processed_at", nullable = false)
        private Instant processedAt = Instant.now();

        public String getEventId() {
            return eventId;
        }
    }

    @Entity
    @Table(name = "order_attributions")
    public static class AttributionEntity {

        @Id
        @Column(name = "order_id")
        private String orderId;

        @Column(name = "customer_id", nullable = false)
        private String customerId;

        @Column(name = "tracking_code", nullable = false)
        private String trackingCode;

        @Column(name = "attributed_at", nullable = false)
        private Instant attributedAt;

        public AttributionEntity() {
        }

        public AttributionEntity(String orderId, String customerId, String trackingCode,
                Instant attributedAt) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.trackingCode = trackingCode;
            this.attributedAt = attributedAt;
        }

        public String getTrackingCode() {
            return trackingCode;
        }
    }

    @Repository
    public static class JdbcProcessedEventStore implements ProcessedEventStore {

        private final JdbcTemplate db;

        public JdbcProcessedEventStore(JdbcTemplate db) {
            this.db = db;
        }

        @Override
        public boolean recordIfAbsent(String eventId) {
            try {
                db.update("insert into processed_events (event_id) values (?)", eventId);
                return true;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                return false;
            }
        }
    }

    @Repository
    public static class JpaAttributionStore implements AttributionStore {

        private final AttributionRepository attributions;

        public JpaAttributionStore(AttributionRepository attributions) {
            this.attributions = attributions;
        }

        @Override
        public void record(Attribution attribution) {
            attributions.save(new AttributionEntity(attribution.orderId(),
                attribution.customerId(), attribution.trackingCode(), attribution.attributedAt()));
        }

        @Override
        public long countByTrackingCode(String trackingCode) {
            return attributions.countByTrackingCode(trackingCode);
        }
    }

    private SmallStores() {
    }
}
