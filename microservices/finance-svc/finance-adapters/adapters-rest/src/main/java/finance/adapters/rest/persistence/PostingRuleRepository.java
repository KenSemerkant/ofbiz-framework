package finance.adapters.rest.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostingRuleRepository extends JpaRepository<PostingRuleEntity, String> {

    Optional<PostingRuleEntity> findByEventType(String eventType);
}
