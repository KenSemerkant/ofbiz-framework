package audit.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** P1-F3-S2 AC: high severity ⇒ alert; info/warn ⇒ no alert. */
class AlertPolicyTest {

    private final AlertPolicy policy = new AlertPolicy();

    @Test
    void highSeverityIsAlertWorthy() {
        assertTrue(policy.isAlertWorthy(event(Severity.HIGH)));
    }

    @Test
    void infoIsNotAlertWorthy() {
        assertFalse(policy.isAlertWorthy(event(Severity.INFO)));
    }

    @Test
    void warnIsNotAlertWorthy() {
        assertFalse(policy.isAlertWorthy(event(Severity.WARN)));
    }

    private AuditEvent event(Severity severity) {
        return new AuditEvent("e1", "audit.secrets.v1", "SECRET_ACCESSED",
            severity, "{}", Instant.now());
    }
}
