package product.application;

import java.time.Instant;
import java.util.Optional;
import product.domain.PriceResolver;
import product.domain.PriceSnapshot;
import product.domain.Product;
import product.domain.ProductPrice;

/**
 * P3-F1-S1 use case: upsert/fetch through the aggregate, outbox events in the
 * same logical transaction. First write emits `product.product.created.v1`,
 * later writes `product.product.updated.v1`; new/changed price rows
 * additionally emit `product.price.changed.v1` (P3-F1-S2).
 */
public class ProductService {

    public static final String PRODUCT_CREATED = "product.product.created.v1";
    public static final String PRODUCT_UPDATED = "product.product.updated.v1";
    public static final String PRODUCT_DELETED = "product.product.deleted.v1";
    public static final String PRICE_CHANGED = "product.price.changed.v1";

    private final ProductStore store;
    private final OutboxWriter outbox;
    private final ProductSerializer serializer;
    private final String defaultCurrencyUomId;

    public ProductService(ProductStore store, OutboxWriter outbox, ProductSerializer serializer,
            String defaultCurrencyUomId) {
        this.store = store;
        this.outbox = outbox;
        this.serializer = serializer;
        this.defaultCurrencyUomId = defaultCurrencyUomId;
    }

    public Optional<Product> get(String productId) {
        return store.find(productId);
    }

    public void upsert(Product product) {
        Optional<Product> existing = store.find(product.productId());
        store.save(product);
        outbox.append(existing.isEmpty() ? PRODUCT_CREATED : PRODUCT_UPDATED,
            product.productId(), serializer.serialize(product));
        emitPriceDeltas(existing.orElse(null), product);
    }

    /** @return false when the product does not exist (no delete, no event). */
    public boolean delete(String productId) {
        if (store.find(productId).isEmpty()) {
            return false;
        }
        store.delete(productId);
        outbox.append(PRODUCT_DELETED, productId, serializer.serializeDeleted(productId));
        return true;
    }

    /**
     * AC (P3-F1-S2): resolve the applicable price at the given instant —
     * date-range rules apply, the requested currency wins over the service
     * default currency. Empty when no price is applicable.
     */
    public Optional<PriceSnapshot> resolvePrice(String productId, String productPriceTypeId,
            String currencyUomId, Instant at) {
        return store.find(productId)
            .flatMap(product -> PriceResolver
                .resolve(product.prices(), productPriceTypeId, currencyUomId,
                    defaultCurrencyUomId, at)
                .map(price -> new PriceSnapshot(productId, price.productPriceTypeId(),
                    price.currencyUomId(), price.price(), at)));
    }

    private void emitPriceDeltas(Product before, Product after) {
        for (ProductPrice price : after.prices()) {
            if (before == null || !before.prices().contains(price)) {
                outbox.append(PRICE_CHANGED, after.productId(),
                    serializer.serializePriceChange(after.productId(), price, false));
            }
        }
        if (before != null) {
            // rows dropped by the update are price changes too (quality review)
            for (ProductPrice price : before.prices()) {
                if (!after.prices().contains(price)) {
                    outbox.append(PRICE_CHANGED, after.productId(),
                        serializer.serializePriceChange(after.productId(), price, true));
                }
            }
        }
    }
}
