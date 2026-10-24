package content.adapters.rest.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import content.application.ContentStore;
import content.domain.Content;

/** JPA adapter for the ContentStore port. */
@Repository
public class JpaContentStore implements ContentStore {

    private final ContentRepository contents;

    public JpaContentStore(ContentRepository contents) {
        this.contents = contents;
    }

    @Override
    public Optional<Content> find(String contentId) {
        return contents.findById(contentId).map(this::toDomain);
    }

    @Override
    public void save(Content content) {
        contents.save(new ContentEntity(content.contentId(), content.contentTypeId(),
            content.contentName(), content.parentContentId(), content.statusId(),
            content.s3Key(), content.contentType(), content.sizeBytes()));
    }

    @Override
    public void remove(String contentId) {
        contents.deleteById(contentId);
    }

    @Override
    public List<Content> findByParentContentId(String parentContentId) {
        return contents.findByParentContentIdOrderByContentIdAsc(parentContentId).stream()
            .map(this::toDomain).toList();
    }

    @Override
    public List<Content> all() {
        return contents.findAllByOrderByContentIdAsc().stream().map(this::toDomain).toList();
    }

    private Content toDomain(ContentEntity entity) {
        return Content.rehydrate(entity.getContentId(), entity.getContentTypeId(),
            entity.getContentName(), entity.getParentContentId(), entity.getStatusId(),
            entity.getS3Key(), entity.getContentType(), entity.getSizeBytes());
    }
}
