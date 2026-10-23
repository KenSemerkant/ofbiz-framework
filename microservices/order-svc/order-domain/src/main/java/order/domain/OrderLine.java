package order.domain;

import java.math.BigDecimal;

/**
 * P4-F1-S2 immutable price snapshot line: the resolved unit price a customer
 * agreed to at placement. Later product-price updates cannot change a
 * captured line (PriceSnapshot contract from P3-F1-S2).
 */
public record OrderLine(String orderItemSeqId, String productId, String facilityId,
        BigDecimal quantity, BigDecimal unitPrice, String currencyUomId) {

    public OrderLine {
        if (orderItemSeqId == null || orderItemSeqId.isBlank()) {
            throw new IllegalArgumentException("orderItemSeqId must not be blank");
        }
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }
        if (facilityId == null || facilityId.isBlank()) {
            throw new IllegalArgumentException("facilityId must not be blank");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice must not be negative");
        }
        if (currencyUomId == null || currencyUomId.isBlank()) {
            throw new IllegalArgumentException("currencyUomId must not be blank");
        }
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(quantity);
    }
}
