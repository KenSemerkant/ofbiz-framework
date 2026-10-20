package audit.adapters.rest;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/** Composite PK of audit_events: the partition key occurred_at must be in the PK. */
@Embeddable
public class AuditEventEntityId implements Serializable {

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    protected AuditEventEntityId() {
    }

    public AuditEventEntityId(String eventId, OffsetDateTime occurredAt) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
    }

    public String getEventId() {
        return eventId;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AuditEventEntityId that)) {
            return false;
        }
        return Objects.equals(eventId, that.eventId) && Objects.equals(occurredAt, that.occurredAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, occurredAt);
    }
}
