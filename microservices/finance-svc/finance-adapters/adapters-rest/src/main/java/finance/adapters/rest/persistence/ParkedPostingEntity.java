package finance.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** parked_postings row (P5-F2-S2): parked for review, never dropped. */
@Entity
@Table(name = "parked_postings")
public class ParkedPostingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_key", nullable = false)
    private String aggregateKey;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private String payload;

    @Column(name = "parked_at", nullable = false)
    private Instant parkedAt = Instant.now();

    public ParkedPostingEntity() {
    }

    public ParkedPostingEntity(String eventType, String aggregateKey, String reason,
            String payload) {
        this.eventType = eventType;
        this.aggregateKey = aggregateKey;
        this.reason = reason;
        this.payload = payload;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateKey() {
        return aggregateKey;
    }

    public String getReason() {
        return reason;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getParkedAt() {
        return parkedAt;
    }
}
