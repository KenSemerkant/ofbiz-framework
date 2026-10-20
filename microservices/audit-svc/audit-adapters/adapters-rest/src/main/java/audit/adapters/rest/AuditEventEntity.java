package audit.adapters.rest;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** JPA mapping of a partitioned audit_events row. */
@Entity
@Table(name = "audit_events")
public class AuditEventEntity {

    @EmbeddedId
    private AuditEventEntityId id;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "payload")
    private String payload;

    @Column(name = "consumed_at", nullable = false)
    private OffsetDateTime consumedAt;

    protected AuditEventEntity() {
    }

    AuditEventEntity(String eventId, String topic, String eventType, String severity,
        String payload, OffsetDateTime occurredAt, OffsetDateTime consumedAt) {
        this.id = new AuditEventEntityId(eventId, occurredAt);
        this.topic = topic;
        this.eventType = eventType;
        this.severity = severity;
        this.payload = payload;
        this.consumedAt = consumedAt;
    }

    public AuditEventEntityId getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getEventType() {
        return eventType;
    }

    public String getSeverity() {
        return severity;
    }

    public String getPayload() {
        return payload;
    }

    public OffsetDateTime getConsumedAt() {
        return consumedAt;
    }
}
