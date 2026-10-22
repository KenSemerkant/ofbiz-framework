package product.application;

import java.util.Optional;
import product.domain.Product;

/**
 * P3-F1-S1 use case: upsert/fetch through the aggregate, outbox events in the
 * same logical transaction. First write emits `product.product.created.v1`,
 * later writes `product.product.updated.v1`.
 */
public class ProductService {

    public static final String PRODUCT_CREATED = "product.product.created.v1";
    public static final String PRODUCT_UPDATED = "product.product.updated.v1";

    private final ProductStore store;
    private final OutboxWriter outbox;
    private final ProductSerializer serializer;

    public ProductService(ProductStore store, OutboxWriter outbox, ProductSerializer serializer) {
        this.store = store;
        this.outbox = outbox;
        this.serializer = serializer;
    }

    public Optional<Product> get(String productId) {
        return store.find(productId);
    }

    public void upsert(Product product) {
        Optional<Product> existing = store.find(product.productId());
        store.save(product);
        outbox.append(existing.isEmpty() ? PRODUCT_CREATED : PRODUCT_UPDATED,
            product.productId(), serializer.serialize(product));
    }
}
