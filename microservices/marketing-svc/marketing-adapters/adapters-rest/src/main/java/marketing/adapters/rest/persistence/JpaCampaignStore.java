package marketing.adapters.rest.persistence;

import java.util.List;
import java.util.Optional;
import marketing.application.MarketingCampaignStore;
import marketing.domain.MarketingCampaign;
import org.springframework.stereotype.Repository;

/** JPA adapter for the MarketingCampaignStore port. */
@Repository
public class JpaCampaignStore implements MarketingCampaignStore {

    private final CampaignRepository campaigns;

    public JpaCampaignStore(CampaignRepository campaigns) {
        this.campaigns = campaigns;
    }

    @Override
    public Optional<MarketingCampaign> find(String marketingCampaignId) {
        return campaigns.findById(marketingCampaignId).map(this::toDomain);
    }

    @Override
    public void save(MarketingCampaign campaign) {
        campaigns.save(new CampaignEntity(campaign.marketingCampaignId(),
            campaign.campaignName(), campaign.statusId(), campaign.fromDate(),
            campaign.thruDate()));
    }

    @Override
    public List<MarketingCampaign> all() {
        return campaigns.findAllByOrderByMarketingCampaignIdAsc().stream().map(this::toDomain)
            .toList();
    }

    private MarketingCampaign toDomain(CampaignEntity entity) {
        return new MarketingCampaign(entity.getMarketingCampaignId(), entity.getCampaignName(),
            entity.getStatusId(), entity.getFromDate(), entity.getThruDate());
    }
}
