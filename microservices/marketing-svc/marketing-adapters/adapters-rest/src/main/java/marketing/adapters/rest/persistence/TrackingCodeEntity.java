package marketing.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** tracking_codes row (P6-T5). */
@Entity
@Table(name = "tracking_codes")
public class TrackingCodeEntity {

    @Id
    @Column(name = "tracking_code_id")
    private String trackingCodeId;

    @Column(name = "marketing_campaign_id")
    private String marketingCampaignId;

    @Column(name = "description")
    private String description;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "thru_date", nullable = false)
    private LocalDate thruDate;

    public TrackingCodeEntity() {
    }

    public TrackingCodeEntity(String trackingCodeId, String marketingCampaignId,
            String description, LocalDate fromDate, LocalDate thruDate) {
        this.trackingCodeId = trackingCodeId;
        this.marketingCampaignId = marketingCampaignId;
        this.description = description;
        this.fromDate = fromDate;
        this.thruDate = thruDate;
    }

    public String getTrackingCodeId() {
        return trackingCodeId;
    }

    public String getMarketingCampaignId() {
        return marketingCampaignId;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getThruDate() {
        return thruDate;
    }
}
