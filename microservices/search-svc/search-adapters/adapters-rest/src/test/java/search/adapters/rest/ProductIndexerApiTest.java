package search.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import search.application.ProductIndexService;
import search.domain.SearchIndex;
import search.domain.SearchQuery;

/**
 * P3-F3-S1 acceptance, end to end against real Kafka + OpenSearch +
 * Postgres: a product event published by product-svc makes the product
 * searchable, the index carries the versioned mapping, and deletes remove
 * the document. The <5s AC is asserted with a wall-clock bound after a
 * warm-up event has absorbed consumer group assignment latency; the
 * on-cluster steady-state proof lands at CG-3 with the same pipeline.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductIndexerApiTest {

    static final String CREATED = "product.product.created.v1";
    static final String UPDATED = "product.product.updated.v1";
    static final String DELETED = "product.product.deleted.v1";
    static final String PRICE_CHANGED = "product.price.changed.v1";
    static final String CATEGORY_UPDATED = "product.category.updated.v1";

    /** AC bound: create/update must be searchable within 5 seconds. */
    static final Duration SEARCHABLE_WITHIN = Duration.ofSeconds(5);

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("search").withUsername("search").withPassword("search");

    // Testcontainers 2.x has no opensearch module — plain container, HTTP on 9200
    @Container
    static final GenericContainer<?> OPENSEARCH =
        new GenericContainer<>("opensearchproject/opensearch:2.11.1")
            .withExposedPorts(9200)
            .withEnv("DISABLE_SECURITY_PLUGIN", "true")
            .withEnv("discovery.type", "single-node")
            .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withStartupTimeout(Duration.ofMinutes(3))
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait
                .forHttp("/").forPort(9200).forStatusCode(200));

    @Autowired
    KafkaTemplate<String, String> kafka;
    @Autowired
    SearchIndex searchIndex;
    @Autowired
    OpenSearchClient openSearch;
    @Autowired
    ProductIndexService indexer;
    @Autowired
    JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("search.opensearch.uris",
            () -> "http://" + OPENSEARCH.getHost() + ":" + OPENSEARCH.getMappedPort(9200));
        try (var socket = new java.net.ServerSocket(0)) {
            registry.add("search.grpc.port", () -> String.valueOf(socket.getLocalPort()));
        }
    }

    @Test
    void productLifecycleIsIndexedWithinBound() throws Exception {
        // warm-up: absorb consumer group assignment; category feeds the name
        // projection the product doc resolves names from
        kafka.send(CATEGORY_UPDATED, "CAT-LAP",
            "{\"productCategoryId\":\"CAT-LAP\",\"categoryName\":\"Laptops\","
                + "\"primaryParentCategoryId\":null}").join();
        String warmId = "WARM-" + UUID.randomUUID();
        kafka.send(CREATED, warmId, productJson(warmId, "Thinkpad Laptop")).join();
        assertTrue(await(() -> !searchIndex.get(ProductIndexService.INDEX, warmId).isEmpty(),
            Duration.ofSeconds(30)), "warm-up product never indexed");

        // create -> searchable < 5s
        String productId = "P-" + UUID.randomUUID();
        kafka.send(CREATED, productId, productJson(productId, "Thinkpad Laptop")).join();
        assertTrue(await(() -> searchable(productId, "thinkpad"), SEARCHABLE_WITHIN),
            "product create did not become searchable within "
                + SEARCHABLE_WITHIN.toSeconds() + "s");

        var fields = searchIndex.get(ProductIndexService.INDEX, productId);
        assertEquals("1", fields.get("schemaVersion"), "doc fields: " + fields);
        assertEquals("Laptops", fields.get("categoryNames"));
        assertEquals("1299.99", fields.get("defaultPrice"));
        assertEquals("USD", fields.get("defaultPriceCurrency"));

        // update -> new name searchable < 5s
        kafka.send(UPDATED, productId, productJson(productId, "Thinkpad Pro Laptop")).join();
        assertTrue(await(() -> searchable(productId, "pro"), SEARCHABLE_WITHIN),
            "product update did not become searchable within the AC bound");

        // price change -> default price refreshed < 5s (best-effort path;
        // product.updated remains authoritative)
        kafka.send(PRICE_CHANGED, productId,
            "{\"productId\":\"" + productId + "\",\"productPriceTypeId\":\"DEFAULT_PRICE\","
                + "\"currencyUomId\":\"USD\",\"price\":1099.99,\"removed\":false}").join();
        assertTrue(await(() -> "1099.99".equals(
                searchIndex.get(ProductIndexService.INDEX, productId).get("defaultPrice")),
            SEARCHABLE_WITHIN), "price change never reached the doc");

        // delete -> gone < 5s
        kafka.send(DELETED, productId, "{\"productId\":\"" + productId + "\"}").join();
        assertTrue(await(() -> searchIndex.get(ProductIndexService.INDEX, productId).isEmpty(),
            SEARCHABLE_WITHIN), "deleted product never left the index");

        // duplicate delivery of a processed event id applies exactly once
        String processedId = jdbc.queryForObject(
            "SELECT event_id FROM processed_events WHERE event_id LIKE '" + DELETED
                + ":%' ORDER BY event_id DESC LIMIT 1", String.class);
        assertFalse(indexer.deleted(processedId, productId),
            "replayed event id must be skipped");
    }

    @Test
    void indexMappingIsVersioned() throws Exception {
        var mapping = openSearch.indices().getMapping(
            request -> request.index(ProductIndexService.INDEX));

        var properties = mapping.result().get(ProductIndexService.INDEX).mappings().properties();
        assertTrue(properties.containsKey("schemaVersion"), "properties: " + properties.keySet());
        assertTrue(properties.containsKey("defaultPrice"));
        assertTrue(properties.get("schemaVersion").isKeyword(),
            "schemaVersion must map as keyword");
    }

    private static String productJson(String productId, String productName) {
        return """
            {"productId":"%s","productTypeId":"FINISHED_GOOD","internalName":"LAP-1",\
            "productName":"%s","categories":["CAT-LAP"],"associations":[],\
            "prices":[{"productPriceTypeId":"DEFAULT_PRICE","currencyUomId":"USD",\
            "fromDate":"2026-01-01T00:00:00Z","thruDate":null,"price":1299.99}]}\
            """.formatted(productId, productName);
    }

    private boolean searchable(String productId, String term) {
        return searchIndex.search(new SearchQuery(term, ProductIndexService.INDEX, 100)).stream()
            .anyMatch(result -> result.docId().equals(productId));
    }

    private static boolean await(java.util.function.BooleanSupplier condition,
            Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(100);
        }
        return condition.getAsBoolean();
    }
}
