package marketing.application;

import java.util.List;
import java.util.Optional;
import marketing.domain.MarketingCampaign;

/** Persistence port for campaigns. */
public interface MarketingCampaignStore {

    Optional<MarketingCampaign> find(String marketingCampaignId);

    void save(MarketingCampaign campaign);

    List<MarketingCampaign> all();
}
