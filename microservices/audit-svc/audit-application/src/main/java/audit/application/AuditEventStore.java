package audit.application;

import audit.domain.AuditEvent;
import java.time.Instant;

/**
 * Port: durable audit event storage (partitioned Postgres behind it).
 */
public interface AuditEventStore {

    /**
     * Persist one event idempotently.
     *
     * @return true if stored, false if the event id was already processed
     */
    boolean append(AuditEvent event);

    /** Query by topic and/or time window, paginated, ordered by occurredAt asc. */
    AuditEventPage search(String topic, Instant from, Instant to, int page, int size);
}
