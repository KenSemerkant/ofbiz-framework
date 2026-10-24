package marketing.adapters.rest.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpportunityRepository extends JpaRepository<OpportunityEntity, String> {

    List<OpportunityEntity> findAllByOrderBySalesOpportunityIdAsc();
}
