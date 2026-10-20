package notification.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** P1-F4-S1 AC c: {placeholder} substitution. */
class TemplateModelTest {

    @Test
    void substitutesSubjectAndBodyParams() {
        TemplateModel model = new TemplateModel("welcome", "de",
                "Willkommen, {name}!", "Hallo {name}, dein Konto {account} ist bereit.");
        RenderedEmail email = model.render(Map.of("name", "Kadir", "account", "K-42"));
        assertEquals("Willkommen, Kadir!", email.subject());
        assertEquals("Hallo Kadir, dein Konto K-42 ist bereit.", email.body());
    }

    @Test
    void unknownPlaceholderLeftAsIs() {
        TemplateModel model = new TemplateModel("welcome", "en", "Hi {name}", "Body");
        assertEquals("Hi {name}", model.render(Map.of()).subject());
    }
}
