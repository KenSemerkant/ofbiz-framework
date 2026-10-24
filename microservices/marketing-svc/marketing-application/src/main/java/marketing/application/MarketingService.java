package marketing.application;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import marketing.domain.CampaignStatus;
import marketing.domain.MarketingCampaign;
import marketing.domain.SalesOpportunity;
import marketing.domain.TrackingCode;

/**
 * P6-T5 aggregate use case: campaigns, tracking codes and opportunities —
 * CRUD with one outbox event per create/update (ADR-009). The tracking
 * code's campaign link is validated FK-by-id inside this context.
 */
public class MarketingService {

    public static final String CAMPAIGN_CREATED = "marketing.campaign.created.v1";
    public static final String CAMPAIGN_UPDATED = "marketing.campaign.updated.v1";
    public static final String TRACKING_CODE_CREATED = "marketing.trackingcode.created.v1";
    public static final String TRACKING_CODE_UPDATED = "marketing.trackingcode.updated.v1";
    public static final String OPPORTUNITY_CREATED = "marketing.opportunity.created.v1";
    public static final String OPPORTUNITY_UPDATED = "marketing.opportunity.updated.v1";

    private final MarketingCampaignStore campaigns;
    private final TrackingCodeStore trackingCodes;
    private final SalesOpportunityStore opportunities;
    private final OutboxWriter outbox;
    private final MarketingSerializer serializer;

    public MarketingService(MarketingCampaignStore campaigns,
            TrackingCodeStore trackingCodes, SalesOpportunityStore opportunities,
            OutboxWriter outbox, MarketingSerializer serializer) {
        this.campaigns = campaigns;
        this.trackingCodes = trackingCodes;
        this.opportunities = opportunities;
        this.outbox = outbox;
        this.serializer = serializer;
    }

    public MarketingCampaign createCampaign(String marketingCampaignId, String campaignName,
            CampaignStatus statusId, LocalDate fromDate, LocalDate thruDate) {
        MarketingCampaign campaign = new MarketingCampaign(marketingCampaignId, campaignName,
            statusId, fromDate, thruDate);
        campaigns.save(campaign);
        outbox.append(CAMPAIGN_CREATED, marketingCampaignId,
            serializer.serializeCampaign(campaign));
        return campaign;
    }

    public Optional<MarketingCampaign> getCampaign(String marketingCampaignId) {
        return campaigns.find(marketingCampaignId);
    }

    public List<MarketingCampaign> campaigns() {
        return campaigns.all();
    }

    /** Partial update: null fields keep their current value. */
    public MarketingCampaign updateCampaign(String marketingCampaignId, String campaignName,
            CampaignStatus statusId, LocalDate fromDate, LocalDate thruDate) {
        MarketingCampaign current = campaigns.find(marketingCampaignId)
            .orElseThrow(() -> new NoSuchElementException("no campaign " + marketingCampaignId));
        MarketingCampaign updated = new MarketingCampaign(marketingCampaignId,
            campaignName != null ? campaignName : current.campaignName(),
            statusId != null ? statusId : current.statusId(),
            fromDate != null ? fromDate : current.fromDate(),
            thruDate != null ? thruDate : current.thruDate());
        campaigns.save(updated);
        outbox.append(CAMPAIGN_UPDATED, marketingCampaignId,
            serializer.serializeCampaign(updated));
        return updated;
    }

    public TrackingCode createTrackingCode(String trackingCodeId, String marketingCampaignId,
            String description, LocalDate fromDate, LocalDate thruDate) {
        if (marketingCampaignId != null && campaigns.find(marketingCampaignId).isEmpty()) {
            throw new NoSuchElementException("no campaign " + marketingCampaignId);
        }
        TrackingCode trackingCode = new TrackingCode(trackingCodeId, marketingCampaignId,
            description, fromDate, thruDate);
        trackingCodes.save(trackingCode);
        outbox.append(TRACKING_CODE_CREATED, trackingCodeId,
            serializer.serializeTrackingCode(trackingCode));
        return trackingCode;
    }

    public Optional<TrackingCode> getTrackingCode(String trackingCodeId) {
        return trackingCodes.find(trackingCodeId);
    }

    public List<TrackingCode> trackingCodes() {
        return trackingCodes.all();
    }

    public TrackingCode updateTrackingCode(String trackingCodeId, String marketingCampaignId,
            String description, LocalDate fromDate, LocalDate thruDate) {
        TrackingCode current = trackingCodes.find(trackingCodeId)
            .orElseThrow(() -> new NoSuchElementException("no tracking code " + trackingCodeId));
        String campaignId = marketingCampaignId != null ? marketingCampaignId
            : current.marketingCampaignId();
        if (campaignId != null && campaigns.find(campaignId).isEmpty()) {
            throw new NoSuchElementException("no campaign " + campaignId);
        }
        TrackingCode updated = new TrackingCode(trackingCodeId, campaignId,
            description != null ? description : current.description(),
            fromDate != null ? fromDate : current.fromDate(),
            thruDate != null ? thruDate : current.thruDate());
        trackingCodes.save(updated);
        outbox.append(TRACKING_CODE_UPDATED, trackingCodeId,
            serializer.serializeTrackingCode(updated));
        return updated;
    }

    public SalesOpportunity createOpportunity(String salesOpportunityId, String description,
            String opportunityStageId, Integer closeProbability) {
        SalesOpportunity opportunity = new SalesOpportunity(salesOpportunityId, description,
            opportunityStageId, closeProbability == null ? 0 : closeProbability);
        opportunities.save(opportunity);
        outbox.append(OPPORTUNITY_CREATED, salesOpportunityId,
            serializer.serializeOpportunity(opportunity));
        return opportunity;
    }

    public Optional<SalesOpportunity> getOpportunity(String salesOpportunityId) {
        return opportunities.find(salesOpportunityId);
    }

    public List<SalesOpportunity> opportunities() {
        return opportunities.all();
    }

    public SalesOpportunity updateOpportunity(String salesOpportunityId, String description,
            String opportunityStageId, Integer closeProbability) {
        SalesOpportunity current = opportunities.find(salesOpportunityId)
            .orElseThrow(() -> new NoSuchElementException(
                "no opportunity " + salesOpportunityId));
        SalesOpportunity updated = new SalesOpportunity(salesOpportunityId,
            description != null ? description : current.description(),
            opportunityStageId != null ? opportunityStageId : current.opportunityStageId(),
            closeProbability != null ? closeProbability : current.closeProbability());
        opportunities.save(updated);
        outbox.append(OPPORTUNITY_UPDATED, salesOpportunityId,
            serializer.serializeOpportunity(updated));
        return updated;
    }
}
