package marketing.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** sales_opportunities row (P6-T5). */
@Entity
@Table(name = "sales_opportunities")
public class OpportunityEntity {

    @Id
    @Column(name = "sales_opportunity_id")
    private String salesOpportunityId;

    @Column(name = "description")
    private String description;

    @Column(name = "opportunity_stage_id", nullable = false)
    private String opportunityStageId;

    @Column(name = "close_probability", nullable = false)
    private int closeProbability;

    public OpportunityEntity() {
    }

    public OpportunityEntity(String salesOpportunityId, String description,
            String opportunityStageId, int closeProbability) {
        this.salesOpportunityId = salesOpportunityId;
        this.description = description;
        this.opportunityStageId = opportunityStageId;
        this.closeProbability = closeProbability;
    }

    public String getSalesOpportunityId() {
        return salesOpportunityId;
    }

    public String getDescription() {
        return description;
    }

    public String getOpportunityStageId() {
        return opportunityStageId;
    }

    public int getCloseProbability() {
        return closeProbability;
    }
}
