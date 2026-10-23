package finance.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** posting_rules row (P5-F2-S2): event type → account pair, configurable. */
@Entity
@Table(name = "posting_rules")
public class PostingRuleEntity {

    @Id
    @Column(name = "rule_id")
    private String ruleId;

    @Column(name = "event_type", nullable = false, unique = true)
    private String eventType;

    @Column(name = "debit_account_id", nullable = false)
    private String debitAccountId;

    @Column(name = "credit_account_id", nullable = false)
    private String creditAccountId;

    @Column(nullable = false)
    private boolean enabled;

    public PostingRuleEntity() {
    }

    public PostingRuleEntity(String ruleId, String eventType, String debitAccountId,
            String creditAccountId, boolean enabled) {
        this.ruleId = ruleId;
        this.eventType = eventType;
        this.debitAccountId = debitAccountId;
        this.creditAccountId = creditAccountId;
        this.enabled = enabled;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getDebitAccountId() {
        return debitAccountId;
    }

    public String getCreditAccountId() {
        return creditAccountId;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
