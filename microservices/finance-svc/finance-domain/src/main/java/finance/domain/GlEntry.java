package finance.domain;

import java.math.BigDecimal;

/** One leg of a journal entry: an amount on exactly one side. */
public record GlEntry(String glAccountId, BigDecimal amount, DebitCredit debitCredit) {

    public GlEntry {
        if (glAccountId == null || glAccountId.isBlank()) {
            throw new IllegalArgumentException("glAccountId must not be blank");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (debitCredit == null) {
            throw new IllegalArgumentException("debitCredit must not be null");
        }
    }

    public static GlEntry debit(String glAccountId, BigDecimal amount) {
        return new GlEntry(glAccountId, amount, DebitCredit.DEBIT);
    }

    public static GlEntry credit(String glAccountId, BigDecimal amount) {
        return new GlEntry(glAccountId, amount, DebitCredit.CREDIT);
    }
}
