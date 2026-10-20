package audit.adapters.rest;

import audit.application.Alert;
import audit.application.AlertPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * P1-F3-S2: HIGH-severity audit events raise an alert on
 * notification.requested.v1 (key = eventId) and bump the alert counter.
 */
@Component
public class KafkaAlertPublisher implements AlertPublisher {

    static final String ALERT_TOPIC = "notification.requested.v1";
    static final String COUNTER_NAME = "audit_alerts_high_total";

    private final KafkaTemplate<String, String> kafka;
    private final Counter alerts;
    private final ObjectMapper mapper;

    public KafkaAlertPublisher(KafkaTemplate<String, String> kafka, MeterRegistry registry,
        ObjectMapper mapper) {
        this.kafka = kafka;
        this.alerts = registry.counter(COUNTER_NAME);
        this.mapper = mapper;
    }

    @Override
    public void publish(Alert alert) {
        String json = mapper.writeValueAsString(Map.of(
            "eventId", alert.eventId(),
            "topic", alert.topic(),
            "eventType", alert.eventType(),
            "severity", alert.severity().name(),
            "occurredAt", alert.occurredAt().toString(),
            "reason", "high-severity audit event"));
        kafka.send(ALERT_TOPIC, alert.eventId(), json);
        alerts.increment();
    }
}
