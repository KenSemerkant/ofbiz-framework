package marketing.application;

import marketing.domain.MarketingCampaign;
import marketing.domain.SalesOpportunity;
import marketing.domain.TrackingCode;

/** Event-payload port: the application layer never touches Jackson. */
public interface MarketingSerializer {

    String serializeCampaign(MarketingCampaign campaign);

    String serializeTrackingCode(TrackingCode trackingCode);

    String serializeOpportunity(SalesOpportunity opportunity);
}
