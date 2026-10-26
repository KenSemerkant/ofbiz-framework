package product.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import product.domain.Store;

/** AC: store settings read/write through the port. */
class StoreServiceTest {

    private final Map<String, Store> storeData = new HashMap<>();
    private final StoreService service = new StoreService(
        new Stores() {
            @Override
            public Optional<Store> find(String productStoreId) {
                return Optional.ofNullable(storeData.get(productStoreId));
            }

            @Override
            public void save(Store store) {
                storeData.put(store.productStoreId(), store);
            }

            @Override
            public boolean delete(String productStoreId) {
                return storeData.remove(productStoreId) != null;
            }

            @Override
            public java.util.List<Store> all() {
                // P9-F2: in-memory listing, ordered by id
                return storeData.values().stream()
                    .sorted((a, b) -> a.productStoreId().compareTo(b.productStoreId()))
                    .toList();
            }
        });

    /** P9-F2: store listing for the SPA catalog area. */
    @Test
    void allListsSavedStores() {
        assertTrue(service.all().isEmpty());
        service.upsert(new Store("S1", "Main", "USD", "WH1"));
        assertEquals(1, service.all().size());
    }

    @Test
    void upsertThenGetRoundtrip() {
        service.upsert(new Store("SHOP", "Web Store", "USD", "WH1"));
        assertEquals("Web Store", service.get("SHOP").orElseThrow().storeName());

        service.upsert(new Store("SHOP", "Retail Store", "EUR", null));
        assertEquals("EUR", service.get("SHOP").orElseThrow().defaultCurrencyUomId());
    }

    @Test
    void unknownStoreIsEmpty() {
        assertTrue(service.get("nope").isEmpty());
    }

    @Test
    void deleteRemovesStore() {
        service.upsert(new Store("SHOP", "Web Store", null, null));
        assertTrue(service.delete("SHOP"));
        assertTrue(service.get("SHOP").isEmpty());
        assertTrue(!service.delete("SHOP"));
    }
}
