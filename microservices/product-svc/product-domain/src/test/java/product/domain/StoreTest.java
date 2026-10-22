package product.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** P3-F1-S3 store settings value object guards. */
class StoreTest {

    @Test
    void carriesKeySettings() {
        Store store = new Store("SHOP", "Web Store", "USD", "WH1");
        assertEquals("SHOP", store.productStoreId());
        assertEquals("Web Store", store.storeName());
        assertEquals("USD", store.defaultCurrencyUomId());
        assertEquals("WH1", store.inventoryFacilityId());
        assertDoesNotThrow(() -> new Store("SHOP", "Web Store", null, null));
    }

    @Test
    void invalidStoresAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Store(" ", "n", null, null));
        assertThrows(IllegalArgumentException.class, () -> new Store("SHOP", " ", null, null));
        assertThrows(IllegalArgumentException.class, () -> new Store("SHOP", "n", " ", null));
        assertThrows(IllegalArgumentException.class, () -> new Store("SHOP", "n", null, " "));
    }
}
