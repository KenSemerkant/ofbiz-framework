package finance.domain;

/**
 * P5-F2-S1 chart-of-accounts member (GlAccount): code + name + the side
 * the account normally posts to (postBalance). Free-form class ids keep
 * the vocabulary lookup-free (finance-svc MAPPING.md).
 */
public final class GlAccount {

    private final String glAccountId;
    private final String accountCode;
    private final String accountName;
    private final DebitCredit postBalance;
    private final String glAccountClassId;

    public GlAccount(String glAccountId, String accountCode, String accountName,
            DebitCredit postBalance, String glAccountClassId) {
        if (glAccountId == null || glAccountId.isBlank()) {
            throw new IllegalArgumentException("glAccountId must not be blank");
        }
        if (accountCode == null || accountCode.isBlank()) {
            throw new IllegalArgumentException("accountCode must not be blank");
        }
        if (accountName == null || accountName.isBlank()) {
            throw new IllegalArgumentException("accountName must not be blank");
        }
        if (postBalance == null) {
            throw new IllegalArgumentException("postBalance must not be null");
        }
        this.glAccountId = glAccountId;
        this.accountCode = accountCode;
        this.accountName = accountName;
        this.postBalance = postBalance;
        this.glAccountClassId = glAccountClassId;
    }

    public String glAccountId() {
        return glAccountId;
    }

    public String accountCode() {
        return accountCode;
    }

    public String accountName() {
        return accountName;
    }

    public DebitCredit postBalance() {
        return postBalance;
    }

    public String glAccountClassId() {
        return glAccountClassId;
    }
}
