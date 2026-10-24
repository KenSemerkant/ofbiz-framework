package marketing.application;

import java.util.List;
import java.util.Optional;
import marketing.domain.SalesOpportunity;

/** Persistence port for sales opportunities. */
public interface SalesOpportunityStore {

    Optional<SalesOpportunity> find(String salesOpportunityId);

    void save(SalesOpportunity opportunity);

    List<SalesOpportunity> all();
}
