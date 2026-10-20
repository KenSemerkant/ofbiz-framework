package notification.application;

/**
 * Dedupe store behind idempotent consumption (ADR-009): backing table is
 * processed_notifications(notification_id pk, processed_at).
 */
public interface NotificationRepository {

    boolean alreadyProcessed(String notificationId);

    void markProcessed(String notificationId);
}
