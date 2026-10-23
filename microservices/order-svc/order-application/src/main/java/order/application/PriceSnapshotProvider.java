package order.application;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Outbound port (P4-F1-S2): resolve the current product price as the
 * immutable snapshot at placement. Adapter calls product-svc.
 */
public interface PriceSnapshotProvider {

    record ResolvedPrice(BigDecimal price, String currencyUomId) {
    }

    /** Empty when no applicable price exists for the product+currency. */
    Optional<ResolvedPrice> resolve(String productId, String currencyUomId);
}
