package notification.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Renderer language fallback and missing-template failure. */
class RendererUnitTest {

    private final ClasspathTemplateRenderer renderer = new ClasspathTemplateRenderer();

    @Test
    void fallsBackToDefaultLanguageAndFailsOnUnknownTemplate() {
        var model = renderer.resolve("welcome", Locale.of("xx"));
        assertEquals("en", model.locale());
        assertThrows(IllegalStateException.class,
            () -> renderer.resolve("no-such-template", Locale.ENGLISH));
    }
}
