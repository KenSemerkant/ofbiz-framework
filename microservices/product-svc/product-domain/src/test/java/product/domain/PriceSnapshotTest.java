package product.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Snapshot-at-order contract (P3-F1-S2 AC): the resolved price is captured as
 * an immutable snapshot DTO — later price updates cannot mutate a snapshot
 * already taken, and re-resolving at a past instant is unaffected by new rows.
 */
class PriceSnapshotTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    void snapshotCapturesTheResolvedPriceImmutably() {
        Product product = new Product("WIDGET", "FINISHED_GOOD", "n", null, null, null);
        product.addPrice(new ProductPrice("DEFAULT_PRICE", "USD", T0, null,
            new BigDecimal("10.00")));

        PriceSnapshot snapshot = PriceResolver
            .resolve(product.prices(), "DEFAULT_PRICE", "USD", "USD", T0)
            .map(p -> new PriceSnapshot(product.productId(), p.productPriceTypeId(),
                p.currencyUomId(), p.price(), T0))
            .orElseThrow();
        assertEquals(new BigDecimal("10.00"), snapshot.price());
        assertEquals("USD", snapshot.currencyUomId());
        assertEquals(T0, snapshot.resolvedAt());

        // a later price update does not mutate the captured snapshot...
        product.addPrice(new ProductPrice("DEFAULT_PRICE", "USD", T1, null,
            new BigDecimal("12.00")));
        assertEquals(new BigDecimal("10.00"), snapshot.price());

        // ...and re-resolving at the order instant still returns the old price
        assertEquals(new BigDecimal("10.00"),
            PriceResolver.resolve(product.prices(), "DEFAULT_PRICE", "USD", "USD", T0)
                .orElseThrow().price());
    }

    @Test
    void aggregatePriceMutators() {
        Product product = new Product("WIDGET", "FINISHED_GOOD", "n", null, null, null);
        ProductPrice price = new ProductPrice("DEFAULT_PRICE", "USD", T0, null,
            new BigDecimal("10.00"));
        assertTrue(product.addPrice(price));
        assertTrue(!product.addPrice(price));
        assertEquals(List.of(price), product.prices());
        assertThrows(IllegalArgumentException.class,
            () -> product.addPrice(new ProductPrice(" ", "USD", T0, null, BigDecimal.ONE)));
        assertThrows(Exception.class, () -> product.prices().add(
            new ProductPrice("X", "USD", T0, null, BigDecimal.ONE)));
    }
}
