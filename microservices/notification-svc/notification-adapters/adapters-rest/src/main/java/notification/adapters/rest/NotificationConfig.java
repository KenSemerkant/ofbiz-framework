package notification.adapters.rest;

import notification.application.MailSender;
import notification.application.NotificationRepository;
import notification.application.RenderNotificationUseCase;
import notification.domain.NotificationRenderer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the application use case from adapter-side port implementations. */
@Configuration
public class NotificationConfig {

    @Bean
    RenderNotificationUseCase renderNotificationUseCase(NotificationRenderer renderer,
            NotificationRepository repository, MailSender mailSender) {
        return new RenderNotificationUseCase(renderer, repository, mailSender);
    }
}
