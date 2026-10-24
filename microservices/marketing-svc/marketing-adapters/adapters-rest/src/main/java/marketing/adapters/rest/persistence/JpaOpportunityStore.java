package marketing.adapters.rest.persistence;

import java.util.List;
import java.util.Optional;
import marketing.application.SalesOpportunityStore;
import marketing.domain.SalesOpportunity;
import org.springframework.stereotype.Repository;

/** JPA adapter for the SalesOpportunityStore port. */
@Repository
public class JpaOpportunityStore implements SalesOpportunityStore {

    private final OpportunityRepository opportunities;

    public JpaOpportunityStore(OpportunityRepository opportunities) {
        this.opportunities = opportunities;
    }

    @Override
    public Optional<SalesOpportunity> find(String salesOpportunityId) {
        return opportunities.findById(salesOpportunityId).map(this::toDomain);
    }

    @Override
    public void save(SalesOpportunity opportunity) {
        opportunities.save(new OpportunityEntity(opportunity.salesOpportunityId(),
            opportunity.description(), opportunity.opportunityStageId(),
            opportunity.closeProbability()));
    }

    @Override
    public List<SalesOpportunity> all() {
        return opportunities.findAllByOrderBySalesOpportunityIdAsc().stream()
            .map(this::toDomain).toList();
    }

    private SalesOpportunity toDomain(OpportunityEntity entity) {
        return new SalesOpportunity(entity.getSalesOpportunityId(), entity.getDescription(),
            entity.getOpportunityStageId(), entity.getCloseProbability());
    }
}
