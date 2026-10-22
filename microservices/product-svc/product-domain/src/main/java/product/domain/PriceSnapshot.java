package product.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable price snapshot captured at order time (P3-F1-S2 AC): the resolved
 * price a customer agreed to; later price updates cannot change a captured
 * snapshot.
 */
public record PriceSnapshot(String productId, String productPriceTypeId, String currencyUomId,
        BigDecimal price, Instant resolvedAt) {
}
