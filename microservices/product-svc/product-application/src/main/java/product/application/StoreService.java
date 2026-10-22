package product.application;

import java.util.Optional;
import product.domain.Store;

/**
 * P3-F1-S3 use case: store settings read/write. No outbox event — the topic
 * set for this story covers product/category/price only; add
 * `product.store.updated.v1` when a consumer actually needs store changes.
 */
public class StoreService {

    private final Stores stores;

    public StoreService(Stores stores) {
        this.stores = stores;
    }

    public Optional<Store> get(String productStoreId) {
        return stores.find(productStoreId);
    }

    public void upsert(Store store) {
        stores.save(store);
    }
}
