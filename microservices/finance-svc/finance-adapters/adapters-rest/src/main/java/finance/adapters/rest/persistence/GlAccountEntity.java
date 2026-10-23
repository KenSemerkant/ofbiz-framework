package finance.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import finance.domain.DebitCredit;

/** gl_accounts row (P5-F2-S1). */
@Entity
@Table(name = "gl_accounts")
public class GlAccountEntity {

    @Id
    @Column(name = "gl_account_id")
    private String glAccountId;

    @Column(name = "account_code", nullable = false)
    private String accountCode;

    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_balance", nullable = false)
    private DebitCredit postBalance;

    @Column(name = "gl_account_class_id")
    private String glAccountClassId;

    public GlAccountEntity() {
    }

    public GlAccountEntity(String glAccountId, String accountCode, String accountName,
            DebitCredit postBalance, String glAccountClassId) {
        this.glAccountId = glAccountId;
        this.accountCode = accountCode;
        this.accountName = accountName;
        this.postBalance = postBalance;
        this.glAccountClassId = glAccountClassId;
    }

    public String getGlAccountId() {
        return glAccountId;
    }

    public String getAccountCode() {
        return accountCode;
    }

    public String getAccountName() {
        return accountName;
    }

    public DebitCredit getPostBalance() {
        return postBalance;
    }

    public String getGlAccountClassId() {
        return glAccountClassId;
    }
}
