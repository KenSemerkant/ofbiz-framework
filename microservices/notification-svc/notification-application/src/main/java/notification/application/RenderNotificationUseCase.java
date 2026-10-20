package notification.application;

import notification.domain.Notification;
import notification.domain.NotificationRenderer;
import notification.domain.RenderedEmail;

/**
 * P1-F4-S1: render a notification and mail it at most once per
 * notificationId — redelivered Kafka events are skipped (idempotent
 * consumption, ADR-009).
 */
public class RenderNotificationUseCase {

    private final NotificationRenderer renderer;
    private final NotificationRepository repository;
    private final MailSender mailSender;

    public RenderNotificationUseCase(NotificationRenderer renderer,
            NotificationRepository repository, MailSender mailSender) {
        this.renderer = renderer;
        this.repository = repository;
        this.mailSender = mailSender;
    }

    public void handle(Notification notification) {
        if (repository.alreadyProcessed(notification.notificationId())) {
            return; // redelivery — mail already sent
        }
        RenderedEmail email = renderer
                .resolve(notification.template(), notification.locale())
                .render(notification.params());
        mailSender.send(notification.recipient(), email);
        repository.markProcessed(notification.notificationId());
    }
}
