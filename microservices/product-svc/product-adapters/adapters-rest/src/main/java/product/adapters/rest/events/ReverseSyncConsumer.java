package product.adapters.rest.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import product.application.ReverseSyncService;
import product.domain.ProductAssoc;
import product.domain.ProductPrice;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * P3-GATE: consumes product-svc's own product.product.* / product.category.*
 * events and writes them back to the monolith tables (new-service wins).
 * Payload shapes are JacksonProductSerializer's; category events carry no
 * productId, deleted events carry only the productId.
 */
@Component
@ConditionalOnProperty(name = "product.monolith.sync.enabled", havingValue = "true")
public class ReverseSyncConsumer {

    private final ReverseSyncService reverseSync;
    private final ObjectMapper json = new ObjectMapper();

    public ReverseSyncConsumer(ReverseSyncService reverseSync) {
        this.reverseSync = reverseSync;
    }

    @KafkaListener(id = "product-reverse-sync",
            topics = {"product.product.created.v1", "product.product.updated.v1",
                    "product.product.deleted.v1", "product.category.updated.v1"},
            properties = {"auto.offset.reset=latest"})
    public void onProductEvent(String payload) {
        JsonNode event = json.readTree(payload);
        if (event.has("productCategoryId")) {
            reverseSync.onCategoryEvent(event.path("productCategoryId").asText(),
                event.path("categoryName").asText(null));
        } else if (event.has("internalName")) {
            reverseSync.onProductEvent(new ReverseSyncService.ProductSnapshot(
                event.path("productId").asText(),
                event.path("productTypeId").asText(),
                event.path("internalName").asText(),
                event.path("productName").asText(null),
                categoryIds(event), prices(event), assocs(event)));
        } else {
            reverseSync.onProductDeleted(event.path("productId").asText());
        }
    }

    private static HashSet<String> categoryIds(JsonNode event) {
        var ids = new HashSet<String>();
        event.path("categories").forEach(category -> ids.add(category.asText()));
        return ids;
    }

    private static List<ProductPrice> prices(JsonNode event) {
        var prices = new HashSet<ProductPrice>();
        event.path("prices").forEach(price -> prices.add(new ProductPrice(
            price.path("productPriceTypeId").asText(),
            price.path("currencyUomId").asText(),
            instant(price.path("fromDate")), instant(price.path("thruDate")),
            amount(price.path("price")))));
        return prices.stream().toList();
    }

    private static List<ProductAssoc> assocs(JsonNode event) {
        var assocs = new HashSet<ProductAssoc>();
        event.path("associations").forEach(assoc -> assocs.add(new ProductAssoc(
            assoc.path("productIdTo").asText(),
            assoc.path("productAssocTypeId").asText(),
            instant(assoc.path("fromDate")), instant(assoc.path("thruDate")))));
        return assocs.stream().toList();
    }

    // Jackson 3 writes Instants as ISO strings, Jackson 2 as epoch millis —
    // the events pass through the outbox relay verbatim, so accept both.
    private static Instant instant(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.isNumber() ? Instant.ofEpochMilli(node.asLong())
            : Instant.parse(node.asText());
    }

    private static BigDecimal amount(JsonNode node) {
        return new BigDecimal(node.asText());
    }
}
