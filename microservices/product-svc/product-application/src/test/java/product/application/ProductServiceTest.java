package product.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import product.domain.Product;

/** AC: outbox emits product.product.created/updated. */
class ProductServiceTest {

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
        },
        (eventType, aggregateKey, payload) -> outboxEvents.add(eventType),
        product -> "{\"productId\":\"" + product.productId() + "\"}");

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
    void getUnknownProductIsEmpty() {
        assertEquals(Optional.empty(), service.get("nope"));
    }
}
