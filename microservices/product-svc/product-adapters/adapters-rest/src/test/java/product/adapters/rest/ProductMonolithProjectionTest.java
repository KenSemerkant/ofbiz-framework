package product.adapters.rest;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import product.application.MonolithProjectionService;

/**
 * P3-GATE AC: the monolith product-slice CDC projection lands, replays are
 * idempotent (topic:partition:offset), and the projection rebuilds from
 * scratch via re-consume.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"product.relay.enabled=false", "product.cdc.enabled=true"})
class ProductMonolithProjectionTest {

    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("product").withUsername("product").withPassword("product");
    private static final KafkaContainer KAFKA =
        new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.0"));

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    }

    @Autowired
    KafkaTemplate<Object, Object> kafka;

    @Autowired
    MonolithProjectionService projectionService;

    @Autowired
    JdbcTemplate jdbc;

    private static String envelope(String table, String op, Map<String, String> after) {
        var fields = new StringBuilder();
        after.forEach((k, v) -> fields.append('"').append(k).append("\":")
            .append(v == null ? "null" : "\"" + v + "\"").append(','));
        fields.setLength(fields.length() - 1);
        String row = "{" + fields + "}";
        String beforeJson = op.equals("d") ? row : "null";
        String afterJson = op.equals("d") ? "null" : row;
        return "{\"op\":\"" + op + "\",\"before\":" + beforeJson + ",\"after\":" + afterJson
            + ",\"source\":{\"table\":\"" + table + "\"}}";
    }

    private void produce(String topic, String payload) {
        kafka.send(topic, payload).join();
    }

    private int count(String table, String where) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " " + where,
            Integer.class);
    }

    @Test
    void cdcEventsProjectAndReplayIsIdempotent() {
        produce("ofbiz.PRODUCT", envelope("PRODUCT", "c",
            Map.of("PRODUCT_ID", "PRD1", "PRODUCT_TYPE_ID", "FINISHED_GOOD",
                "INTERNAL_NAME", "Widget", "PRODUCT_NAME", "Blue Widget")));
        produce("ofbiz.PRODUCT_CATEGORY", envelope("PRODUCT_CATEGORY", "c",
            Map.of("PRODUCT_CATEGORY_ID", "CAT1", "CATEGORY_NAME", "Widgets")));
        produce("ofbiz.PRODUCT_CATEGORY_MEMBER", envelope("PRODUCT_CATEGORY_MEMBER", "c",
            Map.of("PRODUCT_ID", "PRD1", "PRODUCT_CATEGORY_ID", "CAT1")));
        produce("ofbiz.PRODUCT_PRICE", envelope("PRODUCT_PRICE", "c",
            Map.of("PRODUCT_ID", "PRD1", "PRODUCT_PRICE_TYPE_ID", "DEFAULT_PRICE",
                "CURRENCY_UOM_ID", "USD", "FROM_DATE", "0", "PRICE", "19.99")));
        produce("ofbiz.PRODUCT_ASSOC", envelope("PRODUCT_ASSOC", "c",
            Map.of("PRODUCT_ID", "PRD1", "PRODUCT_ID_TO", "PRD2",
                "PRODUCT_ASSOC_TYPE_ID", "PRODUCT_VARIANT", "FROM_DATE", "0")));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals("Blue Widget", jdbc.queryForObject(
                "SELECT product_name FROM monolith_products WHERE product_id='PRD1'",
                String.class));
            assertEquals("Widgets", jdbc.queryForObject(
                "SELECT category_name FROM monolith_product_categories "
                + "WHERE product_category_id='CAT1'", String.class));
            assertEquals(1, count("monolith_product_members",
                "WHERE product_id='PRD1' AND product_category_id='CAT1'"));
            assertEquals("19.99", jdbc.queryForObject(
                "SELECT price FROM monolith_product_prices WHERE product_id='PRD1'",
                String.class));
            assertEquals(1, count("monolith_product_assocs", "WHERE product_id='PRD1'"));
        });

        // duplicate delivery of the same event id — no re-apply
        assertFalse(projectionService.apply("ofbiz.PRODUCT:0:0",
            envelope("PRODUCT", "c", Map.of("PRODUCT_ID", "PRD1",
                "PRODUCT_TYPE_ID", "FINISHED_GOOD", "INTERNAL_NAME", "Changed",
                "PRODUCT_NAME", "Stale"))));
        assertEquals("Blue Widget", jdbc.queryForObject(
            "SELECT product_name FROM monolith_products WHERE product_id='PRD1'",
            String.class));

        // rebuild from scratch: wipe tables + idempotency markers, re-consume
        jdbc.execute("DELETE FROM monolith_product_members");
        jdbc.execute("DELETE FROM monolith_product_prices");
        jdbc.execute("DELETE FROM monolith_product_assocs");
        jdbc.execute("DELETE FROM monolith_products");
        jdbc.execute("DELETE FROM monolith_product_categories");
        jdbc.execute("DELETE FROM processed_events");
        produce("ofbiz.PRODUCT", envelope("PRODUCT", "c",
            Map.of("PRODUCT_ID", "PRD1", "PRODUCT_TYPE_ID", "FINISHED_GOOD",
                "INTERNAL_NAME", "Widget", "PRODUCT_NAME", "Blue Widget")));
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
            assertEquals("Blue Widget", jdbc.queryForObject(
                "SELECT product_name FROM monolith_products WHERE product_id='PRD1'",
                String.class)));
        assertEquals(1, count("monolith_products", ""));
    }

    @Test
    void productDeleteCascadesSliceRowsAndUnknownTableIsIgnored() {
        produce("ofbiz.PRODUCT", envelope("PRODUCT", "c",
            Map.of("PRODUCT_ID", "PRD9", "PRODUCT_TYPE_ID", "FINISHED_GOOD",
                "INTERNAL_NAME", "Doomed", "PRODUCT_NAME", "Doomed")));
        produce("ofbiz.PRODUCT_PRICE", envelope("PRODUCT_PRICE", "c",
            Map.of("PRODUCT_ID", "PRD9", "PRODUCT_PRICE_TYPE_ID", "DEFAULT_PRICE",
                "CURRENCY_UOM_ID", "USD", "FROM_DATE", "0", "PRICE", "1.00")));
        // unknown source table on a subscribed topic: consumed cleanly, no rows
        produce("ofbiz.PRODUCT", envelope("AGREEMENT", "c", Map.of("X", "1")));
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(1, count("monolith_product_prices", "WHERE product_id='PRD9'"));
            assertEquals(0, count("monolith_products", "WHERE product_id='X'"));
        });

        produce("ofbiz.PRODUCT", envelope("PRODUCT", "d",
            Map.of("PRODUCT_ID", "PRD9", "PRODUCT_TYPE_ID", "FINISHED_GOOD",
                "INTERNAL_NAME", "Doomed", "PRODUCT_NAME", "Doomed")));
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(0, count("monolith_products", "WHERE product_id='PRD9'"));
            assertEquals(0, count("monolith_product_prices", "WHERE product_id='PRD9'"));
        });
    }
}
