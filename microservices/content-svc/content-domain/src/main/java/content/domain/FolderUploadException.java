package content.domain;

/** P6-T4: FOLDERs structure the tree; only DOCUMENTs hold object bytes. */
public final class FolderUploadException extends RuntimeException {

    public FolderUploadException(String contentId) {
        super("content %s is a folder and cannot hold bytes".formatted(contentId));
    }
}
