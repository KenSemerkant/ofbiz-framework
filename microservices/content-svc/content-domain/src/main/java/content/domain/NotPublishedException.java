package content.domain;

/** P6-T4: object bytes are fetchable only once the content is PUBLISHED. */
public final class NotPublishedException extends RuntimeException {

    public NotPublishedException(String contentId) {
        super("content %s is not published".formatted(contentId));
    }
}
