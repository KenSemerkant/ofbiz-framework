package notification.adapters.rest;

import java.util.Locale;
import notification.application.RenderNotificationUseCase;
import notification.domain.Notification;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes notification.requested.v1. Any failure (poison JSON, unknown
 * template, SMTP down) propagates: the container retries with backoff and
 * then republishes to the .dlq topic (KafkaConfig).
 */
@Component
public class NotificationRequestListener {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final RenderNotificationUseCase useCase;

    NotificationRequestListener(RenderNotificationUseCase useCase) {
        this.useCase = useCase;
    }

    @KafkaListener(topics = "notification.requested.v1", groupId = "notification-svc")
    public void on(String event) {
        NotificationRequested requested = MAPPER.readValue(event, NotificationRequested.class);
        useCase.handle(new Notification(
                requested.notificationId(),
                requested.channel(),
                requested.recipient(),
                requested.template(),
                Locale.forLanguageTag(requested.locale() == null ? "en" : requested.locale()),
                requested.params()));
    }
}
