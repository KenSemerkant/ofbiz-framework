package finance.application;

import java.util.List;
import java.util.Optional;
import finance.domain.PostingRule;

/** Persistence port for auto-posting rules (P5-F2-S2). */
public interface PostingRuleStore {

    Optional<PostingRule> findByEventType(String eventType);

    void save(PostingRule rule);

    List<PostingRule> all();
}
