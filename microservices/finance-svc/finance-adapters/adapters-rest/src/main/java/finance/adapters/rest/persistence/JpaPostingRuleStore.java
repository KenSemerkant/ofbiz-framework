package finance.adapters.rest.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import finance.application.PostingRuleStore;
import finance.domain.PostingRule;

/** JPA adapter for the PostingRuleStore port. */
@Repository
public class JpaPostingRuleStore implements PostingRuleStore {

    private final PostingRuleRepository rules;

    public JpaPostingRuleStore(PostingRuleRepository rules) {
        this.rules = rules;
    }

    @Override
    public Optional<PostingRule> findByEventType(String eventType) {
        return rules.findByEventType(eventType).map(this::toDomain);
    }

    @Override
    public void save(PostingRule rule) {
        rules.save(new PostingRuleEntity(rule.ruleId(), rule.eventType(),
            rule.debitAccountId(), rule.creditAccountId(), rule.enabled()));
    }

    @Override
    public List<PostingRule> all() {
        return rules.findAll().stream().map(this::toDomain).toList();
    }

    private PostingRule toDomain(PostingRuleEntity entity) {
        return new PostingRule(entity.getRuleId(), entity.getEventType(),
            entity.getDebitAccountId(), entity.getCreditAccountId(), entity.isEnabled());
    }
}
