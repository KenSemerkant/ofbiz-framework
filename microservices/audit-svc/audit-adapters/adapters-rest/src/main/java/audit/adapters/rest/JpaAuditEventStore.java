package audit.adapters.rest;

import audit.application.AuditEventPage;
import audit.application.AuditEventStore;
import audit.domain.AuditEvent;
import audit.domain.Severity;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** JPA-backed AuditEventStore over the partitioned audit_events table. */
@Component
public class JpaAuditEventStore implements AuditEventStore {

    private final AuditEventJpaRepository events;
    private final ProcessedEventRepository processed;
    private final PartitionMaintainer partitions;

    public JpaAuditEventStore(AuditEventJpaRepository events, ProcessedEventRepository processed,
        PartitionMaintainer partitions) {
        this.events = events;
        this.processed = processed;
        this.partitions = partitions;
    }

    @Override
    @Transactional
    public boolean append(AuditEvent event) {
        if (processed.existsById(event.eventId())) {
            return false;
        }
        partitions.ensureFor(event.occurredAt());
        processed.save(new ProcessedEventEntity(event.eventId()));
        OffsetDateTime occurredAt = OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC);
        events.save(new AuditEventEntity(event.eventId(), event.topic(), event.type(),
            event.severity().name(), event.payload(), occurredAt,
            OffsetDateTime.now(ZoneOffset.UTC)));
        return true;
    }

    @Override
    public AuditEventPage search(String topic, Instant from, Instant to, int page, int size) {
        OffsetDateTime fromTs = OffsetDateTime.ofInstant(from, ZoneOffset.UTC);
        OffsetDateTime toTs = OffsetDateTime.ofInstant(to, ZoneOffset.UTC);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id.occurredAt"));
        Page<AuditEventEntity> result = topic == null
            ? events.findByTime(fromTs, toTs, pageable)
            : events.findByTopicAndTime(topic, fromTs, toTs, pageable);
        return new AuditEventPage(result.map(this::toDomain).getContent(), result.getTotalElements());
    }

    private AuditEvent toDomain(AuditEventEntity entity) {
        return new AuditEvent(
            entity.getId().getEventId(),
            entity.getTopic(),
            entity.getEventType(),
            Severity.valueOf(entity.getSeverity()),
            entity.getPayload(),
            entity.getId().getOccurredAt().toInstant());
    }
}
