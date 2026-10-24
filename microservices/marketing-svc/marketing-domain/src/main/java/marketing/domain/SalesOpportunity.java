package marketing.domain;

/**
 * P6-T5 sales opportunity (SalesOpportunity): pipeline record. The close
 * probability is a whole-percent 0-100 — anything else is refused at
 * construction.
 */
public final class SalesOpportunity {

    private final String salesOpportunityId;
    private final String description;
    private final String opportunityStageId;
    private final int closeProbability;

    public SalesOpportunity(String salesOpportunityId, String description,
            String opportunityStageId, int closeProbability) {
        if (salesOpportunityId == null || salesOpportunityId.isBlank()) {
            throw new IllegalArgumentException("salesOpportunityId must not be blank");
        }
        if (opportunityStageId == null || opportunityStageId.isBlank()) {
            throw new IllegalArgumentException("opportunityStageId must not be blank");
        }
        if (closeProbability < 0 || closeProbability > 100) {
            throw new IllegalArgumentException(
                "closeProbability must be between 0 and 100, got " + closeProbability);
        }
        this.salesOpportunityId = salesOpportunityId;
        this.description = description;
        this.opportunityStageId = opportunityStageId;
        this.closeProbability = closeProbability;
    }

    public String salesOpportunityId() {
        return salesOpportunityId;
    }

    public String description() {
        return description;
    }

    public String opportunityStageId() {
        return opportunityStageId;
    }

    public int closeProbability() {
        return closeProbability;
    }
}
