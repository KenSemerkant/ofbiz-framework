package audit.adapters.rest;

import audit.application.RecordAuditEventUseCase;
import audit.domain.AuditEvent;
import audit.domain.Severity;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

/**
 * P1-F3-S1: wildcard audit consumer. Subscribes to every program topic of
 * the audit/demo contexts (v1) — dead-letter topics are excluded.
 */
@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    /** Matches audit and demo context v1 topics; rejects any topic containing "dlq". */
    public static final String TOPIC_PATTERN = "(?![\\w.]*dlq)(audit|demo)\\.[\\w.]+\\.v1";

    /** P1-F3-S2: secret-audit event type. */
    static final String SECRET_EVENT_TYPE = "SECRET_ACCESSED";

    private final RecordAuditEventUseCase useCase;
    private final ObjectMapper mapper;

    public AuditEventConsumer(RecordAuditEventUseCase useCase, ObjectMapper mapper) {
        this.useCase = useCase;
        this.mapper = mapper;
    }

    @KafkaListener(topicPattern = TOPIC_PATTERN)
    void onEvent(ConsumerRecord<String, String> record) {
        try {
            ConsumedEvent event = mapper.readValue(record.value(), ConsumedEvent.class);
            if (event.eventId() == null || event.eventId().isBlank()) {
                log.warn("skipping audit event without eventId on {}", record.topic());
                return;
            }
            useCase.record(new AuditEvent(
                event.eventId(),
                record.topic(),
                event.type() == null ? "UNKNOWN" : event.type(),
                parseSeverity(event.severity()),
                event.payload() == null ? null : event.payload().toString(),
                event.occurredAt() == null ? Instant.now() : Instant.parse(event.occurredAt())));
        } catch (Exception e) {
            log.error("skipping unparseable audit event on {}", record.topic(), e);
        }
    }

    private Severity parseSeverity(String raw) {
        if (raw == null) {
            return Severity.INFO;
        }
        try {
            return Severity.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Severity.INFO;
        }
    }

    /** Wire shape of an audit event on any program topic. */
    record ConsumedEvent(String eventId, String type, String severity, String occurredAt,
        JsonNode payload) {
    }
}
