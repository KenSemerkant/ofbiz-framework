package billing.domain;

import java.math.BigDecimal;

/**
 * P5-F1-S3 on-account billing: credit limit + terms (net days). The
 * outstanding AR balance is maintained by the application layer on
 * issue/settle; the guard lives here so the check cannot be bypassed.
 */
public final class BillingAccount {

    private final String billingAccountId;
    private final String customerId;
    private final BigDecimal creditLimit;
    private BigDecimal outstandingArBalance;
    private Integer netDaysTerm;

    public BillingAccount(String billingAccountId, String customerId, BigDecimal creditLimit,
            Integer netDaysTerm) {
        if (billingAccountId == null || billingAccountId.isBlank()) {
            throw new IllegalArgumentException("billingAccountId must not be blank");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be blank");
        }
        if (creditLimit == null || creditLimit.signum() < 0) {
            throw new IllegalArgumentException("creditLimit must not be negative");
        }
        this.billingAccountId = billingAccountId;
        this.customerId = customerId;
        this.creditLimit = creditLimit;
        this.netDaysTerm = netDaysTerm;
        this.outstandingArBalance = BigDecimal.ZERO;
    }

    /** @throws CreditLimitExceededException when the charge would breach the limit. */
    public void checkCredit(BigDecimal additionalAmount) {
        if (outstandingArBalance().add(additionalAmount).compareTo(creditLimit) > 0) {
            throw new CreditLimitExceededException(billingAccountId,
                creditLimit, outstandingArBalance, additionalAmount);
        }
    }

    public void addAr(BigDecimal amount) {
        checkCredit(amount);
        outstandingArBalance = outstandingArBalance.add(amount);
    }

    public void reduceAr(BigDecimal amount) {
        outstandingArBalance = outstandingArBalance.subtract(amount);
    }

    public static BillingAccount rehydrate(String billingAccountId, String customerId,
            BigDecimal creditLimit, BigDecimal outstandingArBalance, Integer netDaysTerm) {
        BillingAccount account = new BillingAccount(billingAccountId, customerId, creditLimit,
            netDaysTerm);
        account.outstandingArBalance = outstandingArBalance;
        return account;
    }

    public String billingAccountId() {
        return billingAccountId;
    }

    public String customerId() {
        return customerId;
    }

    public BigDecimal creditLimit() {
        return creditLimit;
    }

    public BigDecimal outstandingArBalance() {
        return outstandingArBalance;
    }

    public Integer netDaysTerm() {
        return netDaysTerm;
    }
}
