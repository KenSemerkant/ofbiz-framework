package product.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** P3-F1-S2 price value object: guards and date-range semantics. */
class ProductPriceTest {

    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant THRU = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    void activeWithinHalfOpenRange() {
        ProductPrice price = new ProductPrice("DEFAULT_PRICE", "USD", FROM, THRU,
            new BigDecimal("10.00"));
        assertTrue(price.activeAt(FROM));
        assertTrue(price.activeAt(THRU.minusSeconds(1)));
        assertFalse(price.activeAt(THRU));
        assertFalse(price.activeAt(FROM.minusSeconds(1)));
    }

    @Test
    void openEndedPriceIsActiveForever() {
        ProductPrice price = new ProductPrice("DEFAULT_PRICE", "USD", FROM, null,
            new BigDecimal("10.00"));
        assertTrue(price.activeAt(FROM.plusSeconds(100000)));
    }

    @Test
    void invalidPricesAreRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new ProductPrice(" ", "USD", FROM, null, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
            () -> new ProductPrice("DEFAULT_PRICE", " ", FROM, null, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
            () -> new ProductPrice("DEFAULT_PRICE", "USD", FROM, FROM, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
            () -> new ProductPrice("DEFAULT_PRICE", "USD", FROM, null,
                new BigDecimal("-1")));
        assertThrows(NullPointerException.class,
            () -> new ProductPrice("DEFAULT_PRICE", "USD", null, null, BigDecimal.ONE));
        assertThrows(NullPointerException.class,
            () -> new ProductPrice("DEFAULT_PRICE", "USD", FROM, null, null));
    }
}
