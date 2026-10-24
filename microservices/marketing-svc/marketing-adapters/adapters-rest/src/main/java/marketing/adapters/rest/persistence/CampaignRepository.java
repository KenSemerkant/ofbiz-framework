package marketing.adapters.rest.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository extends JpaRepository<CampaignEntity, String> {

    List<CampaignEntity> findAllByOrderByMarketingCampaignIdAsc();
}
