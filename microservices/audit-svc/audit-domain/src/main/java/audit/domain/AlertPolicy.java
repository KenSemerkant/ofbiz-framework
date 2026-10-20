package audit.domain;

/** Pure domain decision: which audit events raise an alert (P1-F3-S2). */
public class AlertPolicy {

    /** HIGH severity events (including secret-audit events) are alert-worthy. */
    public boolean isAlertWorthy(AuditEvent event) {
        return event.severity() == Severity.HIGH;
    }
}
