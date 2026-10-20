package notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import notification.domain.Notification;
import notification.domain.NotificationRenderer;
import notification.domain.RenderedEmail;
import notification.domain.TemplateModel;
import org.junit.jupiter.api.Test;

/**
 * P1-F4-S1: the use case renders, sends, and never sends twice for the same
 * notificationId — that dedupe is what makes Kafka redelivery safe.
 */
class RenderNotificationUseCaseTest {

    static class FakeRepository implements NotificationRepository {
        final Set<String> processed = new HashSet<>();

        @Override
        public boolean alreadyProcessed(String notificationId) {
            return processed.contains(notificationId);
        }

        @Override
        public void markProcessed(String notificationId) {
            processed.add(notificationId);
        }
    }

    static class RecordingMailSender implements MailSender {
        final List<String> sentTo = new ArrayList<>();

        @Override
        public void send(String recipient, RenderedEmail email) {
            sentTo.add(recipient + "|" + email.subject());
        }
    }

    private final FakeRepository repository = new FakeRepository();
    private final RecordingMailSender mailSender = new RecordingMailSender();
    private final RenderNotificationUseCase useCase = new RenderNotificationUseCase(
            (template, locale) -> new TemplateModel(template, locale.toLanguageTag(),
                    "Willkommen, {name}!", "Hallo {name}!"),
            repository, mailSender);

    private Notification notification(String id) {
        return new Notification(id, "EMAIL", "kadir@example.com", "welcome",
                Locale.GERMAN, Map.of("name", "Kadir"));
    }

    @Test
    void rendersAndSends() {
        useCase.handle(notification("ntf-1"));
        assertEquals(List.of("kadir@example.com|Willkommen, Kadir!"), mailSender.sentTo);
    }

    @Test
    void redeliveryIsSkipped() {
        useCase.handle(notification("ntf-1"));
        useCase.handle(notification("ntf-1")); // Kafka redelivery
        assertEquals(1, mailSender.sentTo.size());
        assertEquals(Set.of("ntf-1"), repository.processed);
    }
}
