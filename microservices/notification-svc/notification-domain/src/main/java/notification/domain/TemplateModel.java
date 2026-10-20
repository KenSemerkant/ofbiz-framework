package notification.domain;

import java.util.Map;

/**
 * A template resolved for one locale. Placeholders are simple {name} tokens;
 * substitution is pure domain logic, so it lives here.
 */
public record TemplateModel(String template, String locale, String subjectTemplate, String bodyTemplate) {

    public RenderedEmail render(Map<String, String> params) {
        return new RenderedEmail(substitute(subjectTemplate, params), substitute(bodyTemplate, params));
    }

    private static String substitute(String text, Map<String, String> params) {
        String result = text;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
