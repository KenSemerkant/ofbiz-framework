package search.adapters.rest;

import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import search.application.ProductIndexService;

/**
 * P3-F3-S1 AC b: the `products` index is created with an explicit field
 * mapping, versioned by the schemaVersion document field (and the mapping
 * below) so mapping evolution is detectable — compare a document's
 * schemaVersion with the current one, or GET the mapping. Bump both when a
 * field is added or changes type, then reindex.
 */
public final class ProductIndexMappings {

    private ProductIndexMappings() {
    }

    /**
     * Creates the products index with the versioned mapping when missing.
     * Called at wiring time, before the Kafka listener containers start, so
     * the first indexed document cannot auto-create the index with dynamic
     * mappings.
     */
    public static void ensureProductsIndex(OpenSearchClient client) {
        try {
            if (client.indices().exists(request -> request.index(ProductIndexService.INDEX))
                    .value()) {
                return;
            }
            client.indices().create(request -> request
                .index(ProductIndexService.INDEX)
                .mappings(ProductIndexMappings::productMappings));
        } catch (OpenSearchException e) {
            // another replica won the create race — the mapping is ours either way
            if (e.response() == null || e.response().status() != 400) {
                throw e;
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                "products index creation failed for " + ProductIndexService.INDEX, e);
        }
    }

    private static TypeMapping.Builder productMappings(TypeMapping.Builder mappings) {
        return mappings
            .properties("schemaVersion", p -> p.keyword(k -> k))
            .properties("productId", p -> p.keyword(k -> k))
            .properties("productTypeId", p -> p.keyword(k -> k))
            .properties("internalName", p -> p.text(t -> t))
            .properties("productName", p -> p.text(t -> t))
            // comma-joined for now: exact per-id filtering needs an array
            // field — a schema v2 change with a reindex
            .properties("categoryIds", p -> p.keyword(k -> k))
            .properties("categoryNames", p -> p.text(t -> t))
            .properties("defaultPrice", p -> p.scaledFloat(f -> f.scalingFactor(100.0)))
            .properties("defaultPriceType", p -> p.keyword(k -> k))
            .properties("defaultPriceCurrency", p -> p.keyword(k -> k))
            .properties("updatedAt", p -> p.date(d -> d));
    }
}
