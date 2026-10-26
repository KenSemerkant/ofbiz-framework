package product.adapters.rest.persistence;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import product.application.Stores;
import product.domain.Store;

/** JPA adapter for the Stores port. */
@Repository
public class JpaStores implements Stores {

    private final StoreRepository stores;

    public JpaStores(StoreRepository stores) {
        this.stores = stores;
    }

    @Override
    public Optional<Store> find(String productStoreId) {
        return stores.findById(productStoreId).map(this::toDomain);
    }

    @Override
    public void save(Store store) {
        stores.save(new StoreEntity(store.productStoreId(), store.storeName(),
            store.defaultCurrencyUomId(), store.inventoryFacilityId()));
    }

    @Override
    public java.util.List<Store> all() {
        // ponytail: in-memory sort — store counts are dozens
        return stores.findAll().stream().map(this::toDomain)
            .sorted(java.util.Comparator.comparing(Store::productStoreId)).toList();
    }

    @Override
    public boolean delete(String productStoreId) {
        if (!stores.existsById(productStoreId)) {
            return false;
        }
        stores.deleteById(productStoreId);
        return true;
    }

    private Store toDomain(StoreEntity entity) {
        return new Store(entity.getProductStoreId(), entity.getStoreName(),
            entity.getDefaultCurrencyUomId(), entity.getInventoryFacilityId());
    }
}
