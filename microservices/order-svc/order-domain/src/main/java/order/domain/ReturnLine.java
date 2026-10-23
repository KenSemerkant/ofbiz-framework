package order.domain;

import java.math.BigDecimal;

/** One returned line: which product, how many units, from which facility. */
public record ReturnLine(String productId, String facilityId, BigDecimal quantity) {

    public ReturnLine {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }
        if (facilityId == null || facilityId.isBlank()) {
            throw new IllegalArgumentException("facilityId must not be blank");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
