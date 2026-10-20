package audit.adapters.rest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Idempotency marker: this event id was already consumed (ADR-009). */
@Entity
@Table(name = "processed_events")
public class ProcessedEventEntity {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    protected ProcessedEventEntity() {
    }

    public ProcessedEventEntity(String eventId) {
        this.eventId = eventId;
        this.consumedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public String getEventId() {
        return eventId;
    }
}
