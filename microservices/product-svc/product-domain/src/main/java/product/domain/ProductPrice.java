package product.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One dated price of a product (OFBiz ProductPrice slice): price type,
 * currency and a half-open from/thru date range. thruDate is null for an
 * open-ended price.
 */
public record ProductPrice(String productPriceTypeId, String currencyUomId,
        Instant fromDate, Instant thruDate, BigDecimal price) {

    public ProductPrice {
        if (productPriceTypeId == null || productPriceTypeId.isBlank()) {
            throw new IllegalArgumentException("productPriceTypeId must not be blank");
        }
        if (currencyUomId == null || currencyUomId.isBlank()) {
            throw new IllegalArgumentException("currencyUomId must not be blank");
        }
        if (fromDate == null) {
            throw new NullPointerException("fromDate must not be null");
        }
        if (thruDate != null && !thruDate.isAfter(fromDate)) {
            throw new IllegalArgumentException("thruDate must be after fromDate");
        }
        if (price == null) {
            throw new NullPointerException("price must not be null");
        }
        if (price.signum() < 0) {
            throw new IllegalArgumentException("price must not be negative");
        }
    }

    public boolean activeAt(Instant at) {
        return !at.isBefore(fromDate) && (thruDate == null || at.isBefore(thruDate));
    }
}
