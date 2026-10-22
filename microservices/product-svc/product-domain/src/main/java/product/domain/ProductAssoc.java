package product.domain;

import java.time.Instant;

/**
 * Directed association from this product to another (OFBiz ProductAssoc).
 * thruDate is exclusive-open in OFBiz; here it must simply postdate fromDate.
 */
public record ProductAssoc(String productIdTo, String productAssocTypeId,
        Instant fromDate, Instant thruDate) {

    public ProductAssoc {
        if (productIdTo == null || productIdTo.isBlank()) {
            throw new IllegalArgumentException("productIdTo must not be blank");
        }
        if (productAssocTypeId == null || productAssocTypeId.isBlank()) {
            throw new IllegalArgumentException("productAssocTypeId must not be blank");
        }
        if (fromDate == null) {
            throw new NullPointerException("fromDate must not be null");
        }
        if (thruDate != null && !thruDate.isAfter(fromDate)) {
            throw new IllegalArgumentException("thruDate must be after fromDate");
        }
    }

    public boolean activeAt(Instant at) {
        return !at.isBefore(fromDate) && (thruDate == null || at.isBefore(thruDate));
    }
}
