package content.domain;

/** P6-T4: a DOCUMENT must have bytes before it can publish. */
public final class MissingUploadException extends RuntimeException {

    public MissingUploadException(String contentId) {
        super("content %s has no upload yet".formatted(contentId));
    }
}
