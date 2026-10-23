package billing.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** billing_accounts row (P5-F1-S3): credit limit + maintained AR balance. */
@Entity
@Table(name = "billing_accounts")
public class BillingAccountEntity {

    @Id
    @Column(name = "billing_account_id")
    private String billingAccountId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "credit_limit", nullable = false, precision = 18, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "outstanding_ar_balance", nullable = false, precision = 18, scale = 2)
    private BigDecimal outstandingArBalance;

    @Column(name = "net_days_term")
    private Integer netDaysTerm;

    public BillingAccountEntity() {
    }

    public BillingAccountEntity(String billingAccountId, String customerId,
            BigDecimal creditLimit, BigDecimal outstandingArBalance, Integer netDaysTerm) {
        this.billingAccountId = billingAccountId;
        this.customerId = customerId;
        this.creditLimit = creditLimit;
        this.outstandingArBalance = outstandingArBalance;
        this.netDaysTerm = netDaysTerm;
    }

    public String getBillingAccountId() {
        return billingAccountId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public BigDecimal getOutstandingArBalance() {
        return outstandingArBalance;
    }

    public Integer getNetDaysTerm() {
        return netDaysTerm;
    }
}
