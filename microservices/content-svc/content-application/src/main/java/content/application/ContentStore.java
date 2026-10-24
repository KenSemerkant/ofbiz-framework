package content.application;

import java.util.List;
import java.util.Optional;
import content.domain.Content;

/** Persistence port for the content tree. */
public interface ContentStore {

    Optional<Content> find(String contentId);

    void save(Content content);

    void remove(String contentId);

    /** Direct children of a node (the ?parent= tree query). */
    List<Content> findByParentContentId(String parentContentId);

    List<Content> all();
}
