package marketing.adapters.rest.persistence;

import marketing.application.MarketingSerializer;
import marketing.domain.MarketingCampaign;
import marketing.domain.SalesOpportunity;
import marketing.domain.TrackingCode;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Event payloads for the marketing.* topics (Jackson 3). */
@Component
public class JacksonMarketingSerializer implements MarketingSerializer {

    private final ObjectMapper json = new ObjectMapper();

    @Override
    public String serializeCampaign(MarketingCampaign campaign) {
        ObjectNode node = json.createObjectNode();
        node.put("marketingCampaignId", campaign.marketingCampaignId());
        node.put("campaignName", campaign.campaignName());
        node.put("statusId", campaign.statusId().name());
        node.put("fromDate", campaign.fromDate().toString());
        node.put("thruDate", campaign.thruDate().toString());
        return node.toString();
    }

    @Override
    public String serializeTrackingCode(TrackingCode trackingCode) {
        ObjectNode node = json.createObjectNode();
        node.put("trackingCodeId", trackingCode.trackingCodeId());
        if (trackingCode.marketingCampaignId() != null) {
            node.put("marketingCampaignId", trackingCode.marketingCampaignId());
        }
        if (trackingCode.description() != null) {
            node.put("description", trackingCode.description());
        }
        node.put("fromDate", trackingCode.fromDate().toString());
        node.put("thruDate", trackingCode.thruDate().toString());
        return node.toString();
    }

    @Override
    public String serializeOpportunity(SalesOpportunity opportunity) {
        ObjectNode node = json.createObjectNode();
        node.put("salesOpportunityId", opportunity.salesOpportunityId());
        if (opportunity.description() != null) {
            node.put("description", opportunity.description());
        }
        node.put("opportunityStageId", opportunity.opportunityStageId());
        node.put("closeProbability", opportunity.closeProbability());
        return node.toString();
    }
}
