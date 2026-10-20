package notification.domain;

/** Result of rendering a template for one notification. */
public record RenderedEmail(String subject, String body) {}
