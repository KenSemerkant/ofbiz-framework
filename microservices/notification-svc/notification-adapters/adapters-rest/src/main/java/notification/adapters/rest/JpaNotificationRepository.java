package notification.adapters.rest;

import java.time.Instant;
import notification.application.NotificationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaNotificationRepository implements NotificationRepository {

    private final ProcessedNotificationJpa jpa;

    JpaNotificationRepository(ProcessedNotificationJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public boolean alreadyProcessed(String notificationId) {
        return jpa.existsById(notificationId);
    }

    @Override
    @Transactional
    public void markProcessed(String notificationId) {
        jpa.save(new ProcessedNotification(notificationId, Instant.now()));
    }
}
