package content.application;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import content.domain.Content;
import content.domain.ContentType;

/**
 * P6-T4 use case: the content tree (create/update/delete, tree query) and
 * the upload→publish→fetch lifecycle. Object bytes go to the ObjectStore
 * (ADR-008) under the deterministic key contents/{id}; the aggregate and
 * its outbox event commit together (ADR-009).
 */
public class ContentService {

    public static final String CONTENT_CREATED = "content.content.created.v1";
    public static final String CONTENT_UPDATED = "content.content.updated.v1";
    public static final String CONTENT_PUBLISHED = "content.content.published.v1";

    /** Published bytes plus their stored media type (the data fetch). */
    public record ContentData(String contentType, byte[] bytes) {
    }

    private final ContentStore contents;
    private final ObjectStore objects;
    private final OutboxWriter outbox;
    private final ContentSerializer serializer;

    public ContentService(ContentStore contents, ObjectStore objects, OutboxWriter outbox,
            ContentSerializer serializer) {
        this.contents = contents;
        this.objects = objects;
        this.outbox = outbox;
        this.serializer = serializer;
    }

    public Optional<Content> find(String contentId) {
        return contents.find(contentId);
    }

    public Content create(String contentId, ContentType contentTypeId, String contentName,
            String parentContentId) {
        requireParent(parentContentId);
        Content content = new Content(contentId, contentTypeId, contentName, parentContentId);
        contents.save(content);
        outbox.append(CONTENT_CREATED, contentId, serializer.serialize(content));
        return content;
    }

    public Content update(String contentId, String contentName, String parentContentId) {
        Content content = load(contentId);
        requireParent(parentContentId);
        content.update(contentName, parentContentId);
        contents.save(content);
        outbox.append(CONTENT_UPDATED, contentId, serializer.serialize(content));
        return content;
    }

    public void delete(String contentId) {
        Content content = load(contentId);
        content.ensureDeletable();
        contents.remove(contentId);
    }

    /** Direct children of a node, or every node when parent is null. */
    public List<Content> tree(String parentContentId) {
        return parentContentId == null ? contents.all()
            : contents.findByParentContentId(parentContentId);
    }

    /**
     * P6-T4 AC: upload→publish→URL fetch. Stores the bytes under
     * contents/{id} (DRAFT DOCUMENTs only — domain-guarded).
     */
    public Content upload(String contentId, byte[] bytes, String contentType) {
        Content content = load(contentId);
        content.upload("contents/" + contentId, contentType, bytes.length);
        objects.put(content.s3Key(), bytes, contentType);
        contents.save(content);
        outbox.append(CONTENT_UPDATED, contentId, serializer.serialize(content));
        return content;
    }

    public Content publish(String contentId) {
        Content content = load(contentId);
        content.publish();
        contents.save(content);
        outbox.append(CONTENT_PUBLISHED, contentId, serializer.serialize(content));
        return content;
    }

    /** PUBLISHED-only byte fetch (DRAFT → NotPublishedException → 409). */
    public ContentData data(String contentId) {
        Content content = load(contentId);
        return new ContentData(content.contentType(), objects.get(content.publishedS3Key()));
    }

    private Content load(String contentId) {
        return contents.find(contentId)
            .orElseThrow(() -> new NoSuchElementException("no content " + contentId));
    }

    private void requireParent(String parentContentId) {
        if (parentContentId != null) {
            load(parentContentId);
        }
    }
}
