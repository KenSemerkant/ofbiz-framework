package audit.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import audit.application.AuditEventPage;
import audit.application.AuditEventStore;
import audit.domain.AuditEvent;
import audit.domain.Severity;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Unit coverage for audit adapter edge paths (no broker/DB needed). */
class AuditAdaptersUnitTest {

    private final List<AuditEvent> recorded = new ArrayList<>();
    private final AuditEventConsumer consumer = new AuditEventConsumer(
        new audit.application.RecordAuditEventUseCase(new AuditEventStore() {
                @Override
                public boolean append(AuditEvent event) {
                    recorded.add(event);
                    return true;
                }

                @Override
                public AuditEventPage search(String topic, java.time.Instant from,
                        java.time.Instant to, int page, int size) {
                    return new AuditEventPage(List.of(), 0);
                }
            },
            alert -> { }, new audit.domain.AlertPolicy()),
        new ObjectMapper());

    private static ConsumerRecord<String, String> rec(String value) {
        return new ConsumerRecord<>("audit.auditevent.recorded.v1", 0, 0L, "k", value);
    }

    @Test
    void consumerHandlesFullAndDegradedEvents() {
        consumer.onEvent(rec("""
            {"eventId":"e1","type":"T","severity":"warn","payload":{"a":1},
             "occurredAt":"2026-10-21T10:00:00Z"}"""));
        assertEquals(Severity.WARN, recorded.get(0).severity());

        consumer.onEvent(rec("""
            {"eventId":"e2","type":null,"severity":"BOGUS","payload":null,
             "occurredAt":null}"""));
        AuditEvent degraded = recorded.get(1);
        assertEquals("UNKNOWN", degraded.type());
        assertEquals(Severity.INFO, degraded.severity());
        assertNotNull(degraded.occurredAt());

        consumer.onEvent(rec("{\"eventId\":\"\"}")); // blank id — skipped
        consumer.onEvent(rec("not-json"));           // unparseable — skipped
        assertEquals(2, recorded.size());
    }

    @Test
    void controllerDefaultsTimeWindowAndMapsNullPayload() {
        List<String> queries = new ArrayList<>();
        AuditEventStore store = new AuditEventStore() {
            @Override
            public boolean append(AuditEvent event) {
                return true;
            }

            @Override
            public AuditEventPage search(String topic, java.time.Instant from,
                    java.time.Instant to, int page, int size) {
                queries.add(topic + "|" + from + "|" + to);
                return new AuditEventPage(List.of(new AuditEvent(
                    "e1", "audit.t", "T", Severity.INFO, null,
                    java.time.Instant.parse("2026-10-21T10:00:00Z"))), 1);
            }
        };
        var controller = new AuditEventController(store);
        var body = controller.query(null, null, null, 0, 20);
        assertEquals(1, ((List<?>) body.get("items")).size());
        assertEquals(1L, body.get("total"));
        assertTrue(queries.get(0).startsWith("null|")); // defaults applied
    }

    @Test
    void compositeIdEqualsHashCode() {
        var a = new AuditEventEntityId(
            "e1", OffsetDateTime.ofInstant(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC));
        var b = new AuditEventEntityId(
            "e1", OffsetDateTime.ofInstant(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertTrue(a.equals(a));
        assertEquals("e1", a.getEventId());
        assertNotNull(a.getOccurredAt());
    }

    @Test
    void processedEventAccessors() {
        assertEquals("e1", new ProcessedEventEntity("e1").getEventId());
    }
}
