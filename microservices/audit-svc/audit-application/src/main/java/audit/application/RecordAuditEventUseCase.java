package audit.application;

import audit.domain.AlertPolicy;
import audit.domain.AuditEvent;

/**
 * P1-F3 core use case: store every consumed audit event idempotently and
 * raise an alert when the domain policy says the event is alert-worthy.
 */
public class RecordAuditEventUseCase {

    private final AuditEventStore store;
    private final AlertPublisher alertPublisher;
    private final AlertPolicy alertPolicy;

    public RecordAuditEventUseCase(AuditEventStore store, AlertPublisher alertPublisher,
        AlertPolicy alertPolicy) {
        this.store = store;
        this.alertPublisher = alertPublisher;
        this.alertPolicy = alertPolicy;
    }

    /**
     * @return true if the event was newly stored, false if it was a duplicate
     */
    public boolean record(AuditEvent event) {
        boolean stored = store.append(event);
        if (stored && alertPolicy.isAlertWorthy(event)) {
            alertPublisher.publish(new Alert(event.eventId(), event.topic(),
                event.type(), event.severity(), event.occurredAt()));
        }
        return stored;
    }
}
