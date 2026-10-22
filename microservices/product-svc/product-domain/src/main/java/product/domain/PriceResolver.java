package product.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * AC (P3-F1-S2): price resolution respects the ProductPrice date-range rules
 * — resolve the applicable price of a type at a given instant, preferring the
 * requested currency and falling back to the default currency. Among
 * overlapping active prices the latest fromDate wins (ties broken by the
 * higher price, so the choice is deterministic).
 */
public final class PriceResolver {

    private static final Comparator<ProductPrice> MOST_RECENT =
        Comparator.comparing(ProductPrice::fromDate).thenComparing(ProductPrice::price);

    private PriceResolver() {
    }

    public static Optional<ProductPrice> resolve(Collection<ProductPrice> prices,
            String productPriceTypeId, String currencyUomId, String defaultCurrencyUomId,
            Instant at) {
        ProductPrice requested = bestActive(prices, productPriceTypeId, currencyUomId, at);
        if (requested != null) {
            return Optional.of(requested);
        }
        return Optional.ofNullable(bestActive(prices, productPriceTypeId, defaultCurrencyUomId,
            at));
    }

    private static ProductPrice bestActive(Collection<ProductPrice> prices,
            String productPriceTypeId, String currencyUomId, Instant at) {
        return prices.stream()
            .filter(p -> p.productPriceTypeId().equals(productPriceTypeId))
            .filter(p -> p.currencyUomId().equals(currencyUomId))
            .filter(p -> p.activeAt(at))
            .max(MOST_RECENT)
            .orElse(null);
    }
}
