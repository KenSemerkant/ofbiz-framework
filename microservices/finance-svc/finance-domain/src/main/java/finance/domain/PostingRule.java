package finance.domain;

/**
 * P5-F2-S2 auto-posting rule (replaces the monolith
 * GlAccountTypeDefault/InvoiceItemTypeGlAccount lookups): event type →
 * debit/credit account pair. Configurable per account mapping; disabled
 * rules are skipped without parking.
 */
public record PostingRule(String ruleId, String eventType, String debitAccountId,
        String creditAccountId, boolean enabled) {

    public PostingRule {
        if (ruleId == null || ruleId.isBlank()) {
            throw new IllegalArgumentException("ruleId must not be blank");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (debitAccountId == null || debitAccountId.isBlank()) {
            throw new IllegalArgumentException("debitAccountId must not be blank");
        }
        if (creditAccountId == null || creditAccountId.isBlank()) {
            throw new IllegalArgumentException("creditAccountId must not be blank");
        }
    }
}
