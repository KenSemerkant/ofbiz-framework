package product.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * AC (P3-F1-S2): price resolution respects date-range rules — among the
 * active prices of the requested type, the requested currency wins over the
 * default currency, and the latest fromDate wins on overlap.
 */
class PriceResolverTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-06-01T00:00:00Z");

    private static ProductPrice price(String type, String currency, Instant from, Instant thru,
            String amount) {
        return new ProductPrice(type, currency, from, thru, new BigDecimal(amount));
    }

    @Test
    void resolvesTheActivePriceAtTheGivenInstant() {
        List<ProductPrice> prices = List.of(
            price("DEFAULT_PRICE", "USD", T0, T1, "10.00"),
            price("DEFAULT_PRICE", "USD", T1, null, "12.00"));
        assertEquals("10.00",
            PriceResolver.resolve(prices, "DEFAULT_PRICE", "USD", "USD", T0.plusSeconds(1))
                .orElseThrow().price().toPlainString());
        assertEquals("12.00",
            PriceResolver.resolve(prices, "DEFAULT_PRICE", "USD", "USD", T1.plusSeconds(1))
                .orElseThrow().price().toPlainString());
    }

    @Test
    void expiredAndFuturePricesAreIgnored() {
        List<ProductPrice> prices = List.of(
            price("DEFAULT_PRICE", "USD", T0.minusSeconds(1000), T0.minusSeconds(1), "9.00"),
            price("DEFAULT_PRICE", "USD", T1, null, "12.00"),
            price("DEFAULT_PRICE", "USD", T0, T1, "10.00"));
        assertEquals("10.00",
            PriceResolver.resolve(prices, "DEFAULT_PRICE", "USD", "USD", T0)
                .orElseThrow().price().toPlainString());
    }

    @Test
    void requestedCurrencyBeatsDefaultCurrency() {
        List<ProductPrice> prices = List.of(
            price("DEFAULT_PRICE", "USD", T0, null, "10.00"),
            price("DEFAULT_PRICE", "EUR", T0, null, "9.00"));
        assertEquals("9.00",
            PriceResolver.resolve(prices, "DEFAULT_PRICE", "EUR", "USD", T0)
                .orElseThrow().price().toPlainString());
    }

    @Test
    void fallsBackToDefaultCurrencyWhenRequestedCurrencyHasNoPrice() {
        List<ProductPrice> prices = List.of(
            price("DEFAULT_PRICE", "USD", T0, null, "10.00"));
        var resolved = PriceResolver.resolve(prices, "DEFAULT_PRICE", "EUR", "USD", T0);
        assertEquals("USD", resolved.orElseThrow().currencyUomId());
    }

    @Test
    void latestFromDateWinsOnOverlap() {
        List<ProductPrice> prices = List.of(
            price("DEFAULT_PRICE", "USD", T0, null, "10.00"),
            price("DEFAULT_PRICE", "USD", T0.plusSeconds(3600), null, "11.00"));
        assertEquals("11.00",
            PriceResolver.resolve(prices, "DEFAULT_PRICE", "USD", "USD", T1)
                .orElseThrow().price().toPlainString());
    }

    @Test
    void onlyPricesOfTheRequestedTypeApply() {
        List<ProductPrice> prices = List.of(
            price("LIST_PRICE", "USD", T0, null, "99.00"),
            price("DEFAULT_PRICE", "USD", T0, null, "10.00"));
        assertEquals("10.00",
            PriceResolver.resolve(prices, "DEFAULT_PRICE", "USD", "USD", T0)
                .orElseThrow().price().toPlainString());
    }

    @Test
    void noApplicablePriceIsEmpty() {
        assertTrue(PriceResolver.resolve(List.of(), "DEFAULT_PRICE", "USD", "USD", T0).isEmpty());
        List<ProductPrice> wrongWindow = List.of(
            price("DEFAULT_PRICE", "USD", T1, null, "12.00"));
        assertTrue(
            PriceResolver.resolve(wrongWindow, "DEFAULT_PRICE", "USD", "USD", T0).isEmpty());
    }
}
