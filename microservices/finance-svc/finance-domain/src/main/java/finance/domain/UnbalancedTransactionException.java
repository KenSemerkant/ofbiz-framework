package finance.domain;

import java.math.BigDecimal;
import java.util.List;

/** P5-F2-S1 double-entry invariant: debits must equal credits to post. */
public final class UnbalancedTransactionException extends RuntimeException {

    public UnbalancedTransactionException(List<GlEntry> entries, BigDecimal debits,
            BigDecimal credits) {
        super("unbalanced transaction (%d entries): debits %s != credits %s".formatted(
            entries.size(), debits, credits));
    }
}
