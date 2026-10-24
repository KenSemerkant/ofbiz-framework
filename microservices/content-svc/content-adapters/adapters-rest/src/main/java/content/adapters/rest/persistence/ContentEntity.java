package content.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import content.domain.ContentStatus;
import content.domain.ContentType;

/** contents row (P6-T4): one tree node; object bytes live in S3 (ADR-008). */
@Entity
@Table(name = "contents")
public class ContentEntity {

    @Id
    @Column(name = "content_id")
    private String contentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type_id", nullable = false)
    private ContentType contentTypeId;

    @Column(name = "content_name", nullable = false)
    private String contentName;

    @Column(name = "parent_content_id")
    private String parentContentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_id", nullable = false)
    private ContentStatus statusId;

    @Column(name = "s3_key")
    private String s3Key;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public ContentEntity() {
    }

    public ContentEntity(String contentId, ContentType contentTypeId, String contentName,
            String parentContentId, ContentStatus statusId, String s3Key, String contentType,
            Long sizeBytes) {
        this.contentId = contentId;
        this.contentTypeId = contentTypeId;
        this.contentName = contentName;
        this.parentContentId = parentContentId;
        this.statusId = statusId;
        this.s3Key = s3Key;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    public String getContentId() {
        return contentId;
    }

    public ContentType getContentTypeId() {
        return contentTypeId;
    }

    public String getContentName() {
        return contentName;
    }

    public String getParentContentId() {
        return parentContentId;
    }

    public ContentStatus getStatusId() {
        return statusId;
    }

    public String getS3Key() {
        return s3Key;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }
}
