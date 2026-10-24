package content.adapters.rest.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentRepository extends JpaRepository<ContentEntity, String> {

    List<ContentEntity> findByParentContentIdOrderByContentIdAsc(String parentContentId);

    List<ContentEntity> findAllByOrderByContentIdAsc();
}
