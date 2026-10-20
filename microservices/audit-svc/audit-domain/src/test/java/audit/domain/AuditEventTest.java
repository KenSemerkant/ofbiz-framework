package audit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuditEventTest {

    @Test
    void valueSemantics() {
        Instant at = Instant.parse("2026-10-20T00:00:00Z");
        AuditEvent event = new AuditEvent("e1", "topic.v1", "TYPE", Severity.HIGH, "{}", at);

        assertEquals("e1", event.eventId());
        assertEquals("topic.v1", event.topic());
        assertEquals("TYPE", event.type());
        assertEquals(Severity.HIGH, event.severity());
        assertEquals("{}", event.payload());
        assertEquals(at, event.occurredAt());
        assertEquals(event, new AuditEvent("e1", "topic.v1", "TYPE", Severity.HIGH, "{}", at));
        assertEquals(event.hashCode(),
            new AuditEvent("e1", "topic.v1", "TYPE", Severity.HIGH, "{}", at).hashCode());
        assertNotEquals(event, new AuditEvent("e2", "topic.v1", "TYPE", Severity.HIGH, "{}", at));
        assertEquals(event.toString(),
            new AuditEvent("e1", "topic.v1", "TYPE", Severity.HIGH, "{}", at).toString());
    }

    @Test
    void severityValuesAreComplete() {
        assertEquals(3, Severity.values().length);
        assertEquals(Severity.INFO, Severity.valueOf("INFO"));
    }
}
