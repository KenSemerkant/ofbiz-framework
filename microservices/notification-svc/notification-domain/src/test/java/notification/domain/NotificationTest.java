package notification.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** P1-F4-S1: a Notification is valid or it does not exist. */
class NotificationTest {

    private Notification valid() {
        return new Notification("ntf-1", "EMAIL", "kadir@example.com", "welcome",
                Locale.GERMAN, Map.of("name", "Kadir"));
    }

    @Test
    void validNotificationConstructs() {
        assertEquals("ntf-1", valid().notificationId());
    }

    @Test
    void nullParamsBecomeEmpty() {
        Notification notification = new Notification("ntf-1", "EMAIL", "kadir@example.com",
                "welcome", Locale.ENGLISH, null);
        assertEquals(Map.of(), notification.params());
    }

    @Test
    void blankNotificationIdRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Notification(" ", "EMAIL",
                "kadir@example.com", "welcome", Locale.ENGLISH, Map.of()));
    }

    @Test
    void nonEmailChannelRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Notification("ntf-1", "SMS",
                "kadir@example.com", "welcome", Locale.ENGLISH, Map.of()));
    }

    @Test
    void blankRecipientRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Notification("ntf-1", "EMAIL",
                " ", "welcome", Locale.ENGLISH, Map.of()));
    }

    @Test
    void unknownTemplateRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Notification("ntf-1", "EMAIL",
                "kadir@example.com", "goodbye", Locale.ENGLISH, Map.of()));
    }
}
