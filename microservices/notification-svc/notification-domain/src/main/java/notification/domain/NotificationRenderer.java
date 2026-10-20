package notification.domain;

import java.util.Locale;

/**
 * Resolves a template for a locale. Implementations fall back to the default
 * locale when the requested one has no template (P1-F4-S1 AC c).
 */
public interface NotificationRenderer {

    TemplateModel resolve(String template, Locale locale);
}
