package product.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import product.domain.Product;
import product.domain.ProductPrice;

/** AC: outbox emits product.product.created/updated and price deltas. */
class ProductServiceTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-06-01T00:00:00Z");

    private final Map<String, Product> storeData = new HashMap<>();
    private final List<String> outboxEvents = new ArrayList<>();

    private final ProductService service = new ProductService(
        new ProductStore() {
            @Override
            public Optional<Product> find(String productId) {
                return Optional.ofNullable(storeData.get(productId));
            }

            @Override
            public void save(Product product) {
                storeData.put(product.productId(), product);
            }

            @Override
            public boolean delete(String productId) {
                return storeData.remove(productId) != null;
            }

            @Override
            public boolean anyInCategory(String categoryId) {
                return storeData.values().stream()
                    .anyMatch(p -> p.categoryIds().contains(categoryId));
            }
        },
        (eventType, aggregateKey, payload) -> outboxEvents.add(eventType),
        new ProductSerializer() {
            @Override
            public String serialize(product.domain.Product product) {
                return "{\"productId\":\"" + product.productId() + "\"}";
            }

            @Override
            public String serializeDeleted(String productId) {
                return "{\"productId\":\"" + productId + "\"}";
            }

            @Override
            public String serializePriceChange(String productId,
                    product.domain.ProductPrice price, boolean removed) {
                return "price-change:" + (removed ? "removed" : "active");
            }
        },
        "USD");

    private static ProductPrice usd(String amount, Instant from, Instant thru) {
        return new ProductPrice("DEFAULT_PRICE", "USD", from, thru, new BigDecimal(amount));
    }

    @Test
    void firstUpsertEmitsCreated() {
        service.upsert(new Product("WIDGET", "FINISHED_GOOD", "Widget", null, null, null));
        assertEquals(List.of(ProductService.PRODUCT_CREATED), outboxEvents);
        assertEquals("Widget", service.get("WIDGET").orElseThrow().internalName());
    }

    @Test
    void secondUpsertEmitsUpdated() {
        service.upsert(new Product("WIDGET", "FINISHED_GOOD", "Widget", null, null, null));
        outboxEvents.clear();
        service.upsert(new Product("WIDGET", "FINISHED_GOOD", "Widget v2", null, null, null));
        assertEquals(List.of(ProductService.PRODUCT_UPDATED), outboxEvents);
    }

    @Test
    void priceDeltasEmitPriceChanged() {
        Product initial = new Product("WIDGET", "FINISHED_GOOD", "Widget", null, null, null);
        initial.addPrice(usd("10.00", T0, null));
        service.upsert(initial);
        assertEquals(List.of(ProductService.PRODUCT_CREATED, ProductService.PRICE_CHANGED),
            outboxEvents);

        outboxEvents.clear();
        Product updated = new Product("WIDGET", "FINISHED_GOOD", "Widget", null, null, null);
        updated.addPrice(usd("10.00", T0, null)); // unchanged row: no event
        updated.addPrice(usd("12.00", T1, null)); // new row: event
        service.upsert(updated);
        assertEquals(List.of(ProductService.PRODUCT_UPDATED, ProductService.PRICE_CHANGED),
            outboxEvents);
    }

    @Test
    void removedPriceRowsEmitPriceChanged() {
        Product initial = new Product("WIDGET", "FINISHED_GOOD", "Widget", null, null, null);
        initial.addPrice(usd("10.00", T0, null));
        initial.addPrice(usd("12.00", T1, null));
        service.upsert(initial);
        outboxEvents.clear();

        Product updated = new Product("WIDGET", "FINISHED_GOOD", "Widget", null, null, null);
        updated.addPrice(usd("12.00", T1, null)); // usd10 row is removed by the update
        service.upsert(updated);
        assertEquals(List.of(ProductService.PRODUCT_UPDATED, ProductService.PRICE_CHANGED),
            outboxEvents);
    }

    @Test
    void resolvePriceRespectsDateRangesAndCurrencyFallback() {
        Product product = new Product("WIDGET", "FINISHED_GOOD", "Widget", null, null, null);
        product.addPrice(usd("10.00", T0, T1));
        product.addPrice(usd("12.00", T1, null));
        service.upsert(product);

        assertEquals("10.00", service
            .resolvePrice("WIDGET", "DEFAULT_PRICE", "USD", T0.plusSeconds(1)).orElseThrow()
            .price().toPlainString());
        assertEquals("12.00", service
            .resolvePrice("WIDGET", "DEFAULT_PRICE", "USD", T1.plusSeconds(1)).orElseThrow()
            .price().toPlainString());
        // EUR requested, only USD priced → default-currency fallback
        assertEquals("USD", service
            .resolvePrice("WIDGET", "DEFAULT_PRICE", "EUR", T0).orElseThrow()
            .currencyUomId());
        assertTrue(service.resolvePrice("WIDGET", "DEFAULT_PRICE", "USD",
            T0.minusSeconds(1)).isEmpty());
        assertTrue(service.resolvePrice("nope", "DEFAULT_PRICE", "USD", T0).isEmpty());
    }

    @Test
    void deleteEmitsDeletedEvent() {
        service.upsert(new Product("WIDGET", "FINISHED_GOOD", "Widget", null, null, null));
        outboxEvents.clear();
        assertTrue(service.delete("WIDGET"));
        assertTrue(service.get("WIDGET").isEmpty());
        assertEquals(List.of(ProductService.PRODUCT_DELETED), outboxEvents);
    }

    @Test
    void deleteUnknownProductIsFalseWithoutEvent() {
        assertTrue(!service.delete("nope"));
        assertTrue(outboxEvents.isEmpty());
    }

    @Test
    void getUnknownProductIsEmpty() {
        assertEquals(Optional.empty(), service.get("nope"));
    }
}
