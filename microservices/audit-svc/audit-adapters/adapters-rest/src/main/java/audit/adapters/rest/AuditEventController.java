package audit.adapters.rest;

import audit.application.AuditEventPage;
import audit.application.AuditEventStore;
import audit.domain.AuditEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * P1-F3-S1 AC: query audit events by topic and/or time window, paginated.
 * GET /api/v1/audit-events?topic=&amp;from=&amp;to=&amp;page=&amp;size=
 */
@RestController
public class AuditEventController {

    private final AuditEventStore store;

    public AuditEventController(AuditEventStore store) {
        this.store = store;
    }

    @GetMapping("/api/v1/audit-events")
    public Map<String, Object> query(
        @RequestParam(required = false) String topic,
        @RequestParam(required = false) Instant from,
        @RequestParam(required = false) Instant to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {

        AuditEventPage result = store.search(topic,
            from != null ? from : Instant.EPOCH,
            to != null ? to : Instant.now().plus(Duration.ofDays(3650)),
            page, size);

        List<Map<String, Object>> items = result.items().stream()
            .map(this::toItem)
            .toList();
        return Map.of("items", items, "page", page, "size", size, "total", result.total());
    }

    private Map<String, Object> toItem(AuditEvent event) {
        return Map.of(
            "eventId", event.eventId(),
            "topic", event.topic(),
            "eventType", event.type(),
            "severity", event.severity().name(),
            "occurredAt", event.occurredAt().toString(),
            "payload", event.payload() == null ? "" : event.payload());
    }
}
