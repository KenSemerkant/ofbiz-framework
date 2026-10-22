package inventory.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** P3-F2-S1 contract: facility identity guards. */
class FacilityTest {

    @Test
    void facilityCarriesIdentityNameAndType() {
        Facility facility = new Facility("WH1", "Main Warehouse", "WAREHOUSE");
        assertEquals("WH1", facility.facilityId());
        assertEquals("Main Warehouse", facility.facilityName());
        assertEquals("WAREHOUSE", facility.facilityTypeId());
    }

    @Test
    void typeIsOptionalButNotBlank() {
        assertEquals("RETAIL", new Facility("SHOP", "Store", "RETAIL").facilityTypeId());
        assertEquals(null, new Facility("SHOP", "Store", null).facilityTypeId());
        assertThrows(IllegalArgumentException.class,
            () -> new Facility("SHOP", "Store", " "));
    }

    @Test
    void blankFieldsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Facility(" ", "n", null));
        assertThrows(IllegalArgumentException.class, () -> new Facility(null, "n", null));
        assertThrows(IllegalArgumentException.class, () -> new Facility("WH1", " ", null));
    }
}
