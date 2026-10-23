package billing.domain;

import java.math.BigDecimal;

/** P5-F1-S3 typed guard: invoice issue refused over the credit limit. */
public final class CreditLimitExceededException extends RuntimeException {

    public CreditLimitExceededException(String billingAccountId, BigDecimal creditLimit,
            BigDecimal outstanding, BigDecimal additional) {
        super("credit limit exceeded on %s: outstanding %s + %s > limit %s".formatted(
            billingAccountId, outstanding, additional, creditLimit));
    }
}
