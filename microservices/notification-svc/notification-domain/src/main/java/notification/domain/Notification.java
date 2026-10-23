package notification.domain;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A notification request consumed from notification.requested.v1 (P1-F4-S1).
 * Validation happens at construction: an invalid notification never enters
 * the use case — a poison message dies on the DLQ, not in the renderer.
 */
public record Notification(
        String notificationId,
        String channel,
        String recipient,
        String template,
        Locale locale,
        Map<String, String> params) {

    /** Only EMAIL is deliverable for now (P1-F4-S1). */
    public static final String CHANNEL_EMAIL = "EMAIL";

    /** Templates shipped on the classpath (templates/&lt;name&gt;_&lt;lang&gt;.properties). */
    public static final Set<String> KNOWN_TEMPLATES
            = Set.of("welcome", "test-command", "invoice-issued");

    public Notification {
        require(nonBlank(notificationId), "notificationId must not be blank");
        require(CHANNEL_EMAIL.equals(channel), "unsupported channel: " + channel);
        require(nonBlank(recipient), "recipient must not be blank");
        require(KNOWN_TEMPLATES.contains(template), "unknown template: " + template);
        require(locale != null, "locale must not be null");
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
