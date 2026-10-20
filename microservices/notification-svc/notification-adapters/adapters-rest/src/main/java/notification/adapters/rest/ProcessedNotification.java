package notification.adapters.rest;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Row in processed_notifications — the idempotent-consumption dedupe key. */
@Entity
@Table(name = "processed_notifications")
class ProcessedNotification {

    @Id
    private String notificationId;

    private Instant processedAt;

    protected ProcessedNotification() {}

    ProcessedNotification(String notificationId, Instant processedAt) {
        this.notificationId = notificationId;
        this.processedAt = processedAt;
    }
}
