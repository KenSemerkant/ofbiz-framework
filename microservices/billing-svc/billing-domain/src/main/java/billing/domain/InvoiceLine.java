package billing.domain;

import java.math.BigDecimal;

/**
 * P5-F1-S1 invoice line: item type + quantity + amount captured from the
 * order snapshot at invoice creation; lines are immutable once issued.
 */
public record InvoiceLine(String invoiceItemSeqId, String invoiceItemTypeId,
        String description, BigDecimal quantity, BigDecimal amount) {

    public InvoiceLine {
        if (invoiceItemSeqId == null || invoiceItemSeqId.isBlank()) {
            throw new IllegalArgumentException("invoiceItemSeqId must not be blank");
        }
        if (invoiceItemTypeId == null || invoiceItemTypeId.isBlank()) {
            throw new IllegalArgumentException("invoiceItemTypeId must not be blank");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
    }

    public BigDecimal lineTotal() {
        return amount.multiply(quantity);
    }
}
