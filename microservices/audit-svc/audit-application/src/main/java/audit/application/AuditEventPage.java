package audit.application;

import audit.domain.AuditEvent;
import java.util.List;

/** One page of audit events plus the total match count. */
public record AuditEventPage(List<AuditEvent> items, long total) {
}
