package product.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** P3-F1-S1 aggregate contract: identity guards, memberships, associations. */
class ProductTest {

    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void upsertRoundtripCarriesNamesAndMemberships() {
        Product product = new Product("WIDGET", "FINISHED_GOOD", "Widget 1.0", "Widget",
            java.util.Set.of("CAT"), null);
        assertEquals("WIDGET", product.productId());
        assertEquals("FINISHED_GOOD", product.productTypeId());
        assertEquals("Widget 1.0", product.internalName());
        assertEquals("Widget", product.productName());
        assertEquals(java.util.Set.of("CAT"), product.categoryIds());
        assertTrue(product.associations().isEmpty());
    }

    @Test
    void blankIdentityFieldsAreRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new Product(" ", "FINISHED_GOOD", "n", null, null, null));
        assertThrows(IllegalArgumentException.class,
            () -> new Product("WIDGET", null, "n", null, null, null));
        assertThrows(IllegalArgumentException.class,
            () -> new Product("WIDGET", "FINISHED_GOOD", " ", null, null, null));
    }

    @Test
    void categoryMembershipLifecycle() {
        Product product = new Product("WIDGET", "FINISHED_GOOD", "n", null, null, null);
        assertTrue(product.addToCategory("CAT"));
        assertFalse(product.addToCategory("CAT"));
        assertTrue(product.removeFromCategory("CAT"));
        assertFalse(product.removeFromCategory("CAT"));
        assertThrows(IllegalArgumentException.class, () -> product.addToCategory(" "));
    }

    @Test
    void associationLifecycle() {
        Product product = new Product("WIDGET", "FINISHED_GOOD", "n", null, null, null);
        ProductAssoc assoc = new ProductAssoc("GIZMO", "PRODUCT_VARIANT", FROM, null);
        assertTrue(product.associateWith(assoc));
        assertFalse(product.associateWith(assoc));
        assertEquals(List.of(assoc), product.associations());
        assertEquals(List.of(assoc), product.activeAssociations(FROM));
        product.associateWith(new ProductAssoc("G2", "MANUFACTURED_WITH", FROM,
            FROM.plusSeconds(5)));
        assertEquals(2, product.activeAssociations(FROM).size());
        // the dated assoc has expired, the open-ended one is still active
        assertEquals(List.of(assoc), product.activeAssociations(FROM.plusSeconds(10)));
        assertThrows(IllegalArgumentException.class, () -> product.associateWith(null));
        assertThrows(IllegalArgumentException.class,
            () -> product.associateWith(new ProductAssoc("WIDGET", "X", FROM, null)));
    }

    @Test
    void assocDateRangeGuards() {
        assertThrows(IllegalArgumentException.class,
            () -> new ProductAssoc("GIZMO", "X", FROM, FROM));
        assertThrows(IllegalArgumentException.class,
            () -> new ProductAssoc(" ", "X", FROM, null));
        assertThrows(IllegalArgumentException.class,
            () -> new ProductAssoc("GIZMO", " ", FROM, null));
        assertThrows(NullPointerException.class,
            () -> new ProductAssoc("GIZMO", "X", null, null));
    }

    @Test
    void nullCollectionsAreTolerated() {
        Product product = new Product("WIDGET", "FINISHED_GOOD", "n", null, null, null);
        assertTrue(product.categoryIds().isEmpty());
        assertTrue(product.associations().isEmpty());
    }

    @Test
    void returnedCollectionsAreDefensiveCopies() {
        Product product = new Product("WIDGET", "FINISHED_GOOD", "n", null, null, null);
        product.addToCategory("CAT");
        assertThrows(UnsupportedOperationException.class,
            () -> product.categoryIds().add("OTHER"));
        assertThrows(UnsupportedOperationException.class,
            () -> product.associations().add(new ProductAssoc("G", "X", FROM, null)));
    }
}
