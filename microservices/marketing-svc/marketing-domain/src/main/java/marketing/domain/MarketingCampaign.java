package marketing.domain;

import java.time.LocalDate;

/**
 * P6-T5 campaign (MarketingCampaign): the promo container tracking codes
 * point at. Status is a closed vocabulary; the date window must be sane.
 */
public final class MarketingCampaign {

    private final String marketingCampaignId;
    private final String campaignName;
    private final CampaignStatus statusId;
    private final LocalDate fromDate;
    private final LocalDate thruDate;

    public MarketingCampaign(String marketingCampaignId, String campaignName,
            CampaignStatus statusId, LocalDate fromDate, LocalDate thruDate) {
        if (marketingCampaignId == null || marketingCampaignId.isBlank()) {
            throw new IllegalArgumentException("marketingCampaignId must not be blank");
        }
        if (campaignName == null || campaignName.isBlank()) {
            throw new IllegalArgumentException("campaignName must not be blank");
        }
        if (statusId == null) {
            throw new IllegalArgumentException("statusId must not be null");
        }
        if (fromDate == null || thruDate == null || thruDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("thruDate must not be before fromDate");
        }
        this.marketingCampaignId = marketingCampaignId;
        this.campaignName = campaignName;
        this.statusId = statusId;
        this.fromDate = fromDate;
        this.thruDate = thruDate;
    }

    public String marketingCampaignId() {
        return marketingCampaignId;
    }

    public String campaignName() {
        return campaignName;
    }

    public CampaignStatus statusId() {
        return statusId;
    }

    public LocalDate fromDate() {
        return fromDate;
    }

    public LocalDate thruDate() {
        return thruDate;
    }
}
