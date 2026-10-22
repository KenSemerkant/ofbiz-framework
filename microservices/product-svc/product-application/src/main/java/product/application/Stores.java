package product.application;

import java.util.Optional;
import product.domain.Store;

/** Persistence port for the store settings. */
public interface Stores {

    Optional<Store> find(String productStoreId);

    void save(Store store);

    /** @return true if a row existed and was removed. */
    boolean delete(String productStoreId);
}
