package audit.application;

import audit.domain.Severity;
import java.time.Instant;

/** Alert raised for an alert-worthy audit event (P1-F3-S2). */
public record Alert(
    String eventId,
    String topic,
    String eventType,
    Severity severity,
    Instant occurredAt) {
}
