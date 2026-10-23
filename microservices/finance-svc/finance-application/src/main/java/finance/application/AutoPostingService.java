package finance.application;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;
import finance.domain.GlEntry;
import finance.domain.GlTransaction;
import finance.domain.PostingRule;

/**
 * P5-F2-S2 auto-posting consumer use case (replaces the accounting ECAs):
 * billing.invoice.issued/settled → journal entry per the configured rule.
 * A missing rule/account or a guard failure parks the event for review —
 * nothing is ever dropped silently.
 */
public class AutoPostingService {

    public static final String TRANSACTION_POSTED = "finance.transaction.posted.v1";

    private final GlService gl;
    private final PostingRuleStore rules;
    private final ParkedPostingStore parked;

    public AutoPostingService(GlService gl, PostingRuleStore rules, ParkedPostingStore parked) {
        this.gl = gl;
        this.rules = rules;
        this.parked = parked;
    }

    /**
     * @return the posted transaction id, or null when parked/skipped — the
     *         caller never retries a parked event (parking IS the record).
     */
    public String onBillingEvent(String eventType, String aggregateKey, BigDecimal amount,
            String currencyUomId, String description) {
        Optional<PostingRule> rule = rules.findByEventType(eventType);
        if (rule.isEmpty() || !rule.orElseThrow().enabled()) {
            return null;
        }
        PostingRule postingRule = rule.orElseThrow();
        // one posting per (event, aggregate): issued and settled for the same
        // invoice are different journal entries
        String action = eventType.replaceAll("\\.v\\d+$", "");
        String transId = "ATT-" + aggregateKey + "-"
            + action.substring(action.lastIndexOf('.') + 1);
        if (gl.find(transId).isPresent()) {
            return transId;
        }
        try {
            GlTransaction transaction = gl.postTransaction(transId, "AUTO_" + eventType, description,
                gl.defaultOpenPeriodId(), java.util.List.of(
                    GlEntry.debit(postingRule.debitAccountId(), amount),
                    GlEntry.credit(postingRule.creditAccountId(), amount)));
            return transaction.acctgTransId();
        } catch (NoSuchElementException | IllegalStateException e) {
            parked.park(eventType, aggregateKey, e.getMessage(), description);
            return null;
        }
    }
}
