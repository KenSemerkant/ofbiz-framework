package marketing.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "outbox")
public class OutboxEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_key", nullable = false)
    private String aggregateKey;

    @Column(nullable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    public OutboxEntryEntity() {
    }

    public OutboxEntryEntity(String eventType, String aggregateKey, String payload) {
        this.eventType = eventType;
        this.aggregateKey = aggregateKey;
        this.payload = payload;
    }

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateKey() {
        return aggregateKey;
    }

    public String getPayload() {
        return payload;
    }

    public void markPublished(Instant at) {
        this.publishedAt = at;
    }
}
