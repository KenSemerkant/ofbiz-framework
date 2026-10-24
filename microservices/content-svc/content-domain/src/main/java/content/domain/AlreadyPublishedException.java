package content.domain;

/** Publishing is forward-only: a published node never re-publishes (P6-T4). */
public final class AlreadyPublishedException extends RuntimeException {

    public AlreadyPublishedException(String contentId) {
        super("content %s is already published".formatted(contentId));
    }
}
