package marketing.domain;

import java.time.LocalDate;

/**
 * P6-T5 tracking code (TrackingCode): the attribution join key an order
 * carries. The campaign link is an FK-by-id and may be null (uncoded
 * campaigns, affiliate-only codes).
 */
public final class TrackingCode {

    private final String trackingCodeId;
    private final String marketingCampaignId;
    private final String description;
    private final LocalDate fromDate;
    private final LocalDate thruDate;

    public TrackingCode(String trackingCodeId, String marketingCampaignId, String description,
            LocalDate fromDate, LocalDate thruDate) {
        if (trackingCodeId == null || trackingCodeId.isBlank()) {
            throw new IllegalArgumentException("trackingCodeId must not be blank");
        }
        if (fromDate == null || thruDate == null || thruDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("thruDate must not be before fromDate");
        }
        this.trackingCodeId = trackingCodeId;
        this.marketingCampaignId = marketingCampaignId;
        this.description = description;
        this.fromDate = fromDate;
        this.thruDate = thruDate;
    }

    public String trackingCodeId() {
        return trackingCodeId;
    }

    /** Campaign reference by id only — never a cross-context join (ADR-008). */
    public String marketingCampaignId() {
        return marketingCampaignId;
    }

    public String description() {
        return description;
    }

    public LocalDate fromDate() {
        return fromDate;
    }

    public LocalDate thruDate() {
        return thruDate;
    }
}
