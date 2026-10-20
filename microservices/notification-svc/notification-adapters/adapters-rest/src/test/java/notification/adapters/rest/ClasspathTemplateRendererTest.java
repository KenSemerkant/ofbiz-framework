package notification.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** P1-F4-S1 AC c: locale pick with en fallback, params substituted. */
class ClasspathTemplateRendererTest {

    private final ClasspathTemplateRenderer renderer = new ClasspathTemplateRenderer();

    @Test
    void rendersGermanForDe() {
        var model = renderer.resolve("welcome", Locale.GERMAN);
        assertEquals("de", model.locale());
        var email = model.render(Map.of("name", "Kadir"));
        assertEquals("Willkommen bei OFBiz, Kadir!", email.subject());
    }

    @Test
    void germanBodyHasSubstitutedParams() {
        var email = renderer.resolve("welcome", Locale.GERMAN).render(Map.of("name", "Kadir"));
        assertEquals("Hallo Kadir,", email.body().substring(0, "Hallo Kadir,".length()));
    }

    @Test
    void unknownLocaleFallsBackToEn() {
        var model = renderer.resolve("welcome", Locale.forLanguageTag("fr"));
        assertEquals("en", model.locale());
        assertEquals("Welcome to OFBiz, Kadir!", model.render(Map.of("name", "Kadir")).subject());
    }
}
