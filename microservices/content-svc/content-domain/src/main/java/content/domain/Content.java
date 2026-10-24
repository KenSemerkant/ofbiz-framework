package content.domain;

/**
 * P6-T4 content node (Content + its DataResource, merged): FOLDERs build
 * the tree, DOCUMENTs hold one S3 object (bytes live in the object store
 * per ADR-008 — never a DB BLOB). Publishing is forward-only: a DOCUMENT
 * publishes once, after upload, and is frozen afterwards.
 */
public final class Content {

    private final String contentId;
    private final ContentType contentTypeId;
    private String contentName;
    private String parentContentId;
    private ContentStatus statusId;
    private String s3Key;
    private String contentType;
    private Long sizeBytes;

    public Content(String contentId, ContentType contentTypeId, String contentName,
            String parentContentId) {
        if (contentId == null || contentId.isBlank()) {
            throw new IllegalArgumentException("contentId must not be blank");
        }
        if (contentTypeId == null) {
            throw new IllegalArgumentException("contentTypeId must not be null");
        }
        if (contentName == null || contentName.isBlank()) {
            throw new IllegalArgumentException("contentName must not be blank");
        }
        this.contentId = contentId;
        this.contentTypeId = contentTypeId;
        this.contentName = contentName;
        this.parentContentId = parentContentId;
        this.statusId = ContentStatus.DRAFT;
    }

    /**
     * Records a stored object against this DOCUMENT (P6-T4 AC: DRAFT only).
     *
     * @throws FolderUploadException     when a FOLDER is uploaded to
     * @throws AlreadyPublishedException when the content is already published
     */
    public void upload(String s3Key, String contentType, long sizeBytes) {
        if (contentTypeId == ContentType.FOLDER) {
            throw new FolderUploadException(contentId);
        }
        if (statusId == ContentStatus.PUBLISHED) {
            throw new AlreadyPublishedException(contentId);
        }
        if (s3Key == null || s3Key.isBlank()) {
            throw new IllegalArgumentException("s3Key must not be blank");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType must not be blank");
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
        this.s3Key = s3Key;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    /**
     * @throws AlreadyPublishedException on re-publish (publish is one-way)
     * @throws MissingUploadException    when nothing was uploaded yet
     */
    public void publish() {
        if (statusId == ContentStatus.PUBLISHED) {
            throw new AlreadyPublishedException(contentId);
        }
        if (s3Key == null) {
            throw new MissingUploadException(contentId);
        }
        statusId = ContentStatus.PUBLISHED;
    }

    /** Renames / reparents a DRAFT node; published content is frozen. */
    public void update(String contentName, String parentContentId) {
        if (statusId == ContentStatus.PUBLISHED) {
            throw new AlreadyPublishedException(contentId);
        }
        if (contentName == null || contentName.isBlank()) {
            throw new IllegalArgumentException("contentName must not be blank");
        }
        this.contentName = contentName;
        this.parentContentId = parentContentId;
    }

    /** @throws AlreadyPublishedException when a published node is deleted. */
    public void ensureDeletable() {
        if (statusId == ContentStatus.PUBLISHED) {
            throw new AlreadyPublishedException(contentId);
        }
    }

    /**
     * @return the S3 key backing the published bytes
     * @throws NotPublishedException while still DRAFT (P6-T4: data fetch is
     *         PUBLISHED-only; DRAFT → 409)
     */
    public String publishedS3Key() {
        if (statusId != ContentStatus.PUBLISHED) {
            throw new NotPublishedException(contentId);
        }
        return s3Key;
    }

    /** Rehydration factory: trusted persisted state, guards not re-run. */
    public static Content rehydrate(String contentId, ContentType contentTypeId,
            String contentName, String parentContentId, ContentStatus statusId, String s3Key,
            String contentType, Long sizeBytes) {
        Content content = new Content(contentId, contentTypeId, contentName, parentContentId);
        content.statusId = statusId;
        content.s3Key = s3Key;
        content.contentType = contentType;
        content.sizeBytes = sizeBytes;
        return content;
    }

    public String contentId() {
        return contentId;
    }

    public ContentType contentTypeId() {
        return contentTypeId;
    }

    public String contentName() {
        return contentName;
    }

    public String parentContentId() {
        return parentContentId;
    }

    public ContentStatus statusId() {
        return statusId;
    }

    public String s3Key() {
        return s3Key;
    }

    public String contentType() {
        return contentType;
    }

    public Long sizeBytes() {
        return sizeBytes;
    }
}
