package notification.adapters.rest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Properties;
import notification.domain.NotificationRenderer;
import notification.domain.TemplateModel;
import org.springframework.stereotype.Component;

/**
 * Resolves templates from classpath:/templates/&lt;name&gt;_&lt;lang&gt;.properties,
 * falling back to en when the requested locale has no template (P1-F4-S1
 * AC c). Template names arrive domain-validated (Notification.KNOWN_TEMPLATES).
 */
@Component
public class ClasspathTemplateRenderer implements NotificationRenderer {

    static final String DEFAULT_LANGUAGE = "en";

    @Override
    public TemplateModel resolve(String template, Locale locale) {
        String language = locale.getLanguage();
        Properties props = load(template, language);
        if (props == null && !DEFAULT_LANGUAGE.equals(language)) {
            language = DEFAULT_LANGUAGE;
            props = load(template, language);
        }
        if (props == null) {
            throw new IllegalStateException("no template found for " + template);
        }
        return new TemplateModel(template, language,
                props.getProperty("subject"), props.getProperty("body"));
    }

    private Properties load(String template, String language) {
        String path = "/templates/" + template + "_" + language + ".properties";
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            Properties props = new Properties();
            props.load(in);
            return props;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + path, e);
        }
    }
}
