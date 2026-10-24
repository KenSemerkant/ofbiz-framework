package marketing.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import marketing.domain.CampaignStatus;

/** marketing_campaigns row (P6-T5). */
@Entity
@Table(name = "marketing_campaigns")
public class CampaignEntity {

    @Id
    @Column(name = "marketing_campaign_id")
    private String marketingCampaignId;

    @Column(name = "campaign_name", nullable = false)
    private String campaignName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_id", nullable = false)
    private CampaignStatus statusId;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "thru_date", nullable = false)
    private LocalDate thruDate;

    public CampaignEntity() {
    }

    public CampaignEntity(String marketingCampaignId, String campaignName,
            CampaignStatus statusId, LocalDate fromDate, LocalDate thruDate) {
        this.marketingCampaignId = marketingCampaignId;
        this.campaignName = campaignName;
        this.statusId = statusId;
        this.fromDate = fromDate;
        this.thruDate = thruDate;
    }

    public String getMarketingCampaignId() {
        return marketingCampaignId;
    }

    public String getCampaignName() {
        return campaignName;
    }

    public CampaignStatus getStatusId() {
        return statusId;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getThruDate() {
        return thruDate;
    }
}
