package search.adapters.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import search.application.ProductIndexService;

/**
 * P3-F3-S1 AC a: product create/update/delete events make the product
 * searchable. Topic names are the contract with product-svc
 * (ProductService constants — duplicated here on purpose: no cross-service
 * code dependency). The event id is topic:partition:offset; payloads are
 * whatever product-svc's outbox relay carries (no envelope).
 */
@Component
public class ProductEventListener {

    static final String PRODUCT_CREATED = "product.product.created.v1";
    static final String PRODUCT_UPDATED = "product.product.updated.v1";
    static final String PRODUCT_DELETED = "product.product.deleted.v1";
    static final String PRICE_CHANGED = "product.price.changed.v1";
    static final String CATEGORY_UPDATED = "product.category.updated.v1";

    private final ProductIndexService indexer;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProductEventListener(ProductIndexService indexer) {
        this.indexer = indexer;
    }

    @KafkaListener(id = "search-product-index", topics = {
            PRODUCT_CREATED, PRODUCT_UPDATED, PRODUCT_DELETED, PRICE_CHANGED, CATEGORY_UPDATED},
        properties = {"auto.offset.reset=earliest"})
    public void onProductEvent(ConsumerRecord<String, String> record) {
        String eventId = "%s:%d:%d".formatted(record.topic(), record.partition(), record.offset());
        JsonNode event = parse(record.value());
        switch (record.topic()) {
            case PRODUCT_CREATED, PRODUCT_UPDATED ->
                indexer.upserted(eventId, snapshot(event));
            case PRODUCT_DELETED -> indexer.deleted(eventId, text(event, "productId"));
            case PRICE_CHANGED -> indexer.priceChanged(eventId, text(event, "productId"),
                text(event, "productPriceTypeId"), text(event, "currencyUomId"),
                event.path("price").asText(), event.path("removed").asBoolean(false));
            case CATEGORY_UPDATED -> indexer.categoryUpdated(eventId,
                text(event, "productCategoryId"), text(event, "categoryName"));
            default -> { /* not a product topic — nothing to do */ }
        }
    }

    /** Payload of product.product.created/updated.v1 (JacksonProductSerializer shape). */
    static ProductIndexService.ProductSnapshot snapshot(JsonNode event) {
        List<String> categoryIds = new ArrayList<>();
        event.path("categories").forEach(id -> categoryIds.add(id.asText()));
        List<ProductIndexService.Price> prices = new ArrayList<>();
        event.path("prices").forEach(price -> prices.add(new ProductIndexService.Price(
            text(price, "productPriceTypeId"), text(price, "currencyUomId"),
            price.path("price").asText())));
        return new ProductIndexService.ProductSnapshot(text(event, "productId"),
            text(event, "productTypeId"), text(event, "internalName"),
            text(event, "productName"), List.copyOf(categoryIds), prices);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private JsonNode parse(String payload) {
        try {
            return mapper.readTree(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("bad product event payload: " + payload, e);
        }
    }
}
