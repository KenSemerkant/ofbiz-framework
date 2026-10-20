package audit.domain;

import java.time.Instant;

/**
 * An audit event consumed from any program topic (ADR-011: audit-svc
 * consumes a wildcard of domain topics and retains for review).
 *
 * @param eventId   source event id (idempotency key)
 * @param topic     Kafka topic the event was consumed from
 * @param type      event type, e.g. SECRET_ACCESSED
 * @param severity  INFO / WARN / HIGH
 * @param payload   raw payload JSON
 * @param occurredAt business time of the event
 */
public record AuditEvent(
    String eventId,
    String topic,
    String type,
    Severity severity,
    String payload,
    Instant occurredAt) {
}
