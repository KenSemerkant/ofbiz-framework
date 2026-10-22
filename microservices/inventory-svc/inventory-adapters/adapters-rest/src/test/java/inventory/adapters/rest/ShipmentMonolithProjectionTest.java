package inventory.adapters.rest;

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
import inventory.application.MonolithProjectionService;

/**
 * P3-GATE AC: the monolith shipment-slice CDC projection lands, replays
 * are idempotent (topic:partition:offset), a SHIPMENT delete cascades its
 * items, and the projection rebuilds from scratch via re-consume.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"inventory.relay.enabled=false", "inventory.cdc.enabled=true"})
class ShipmentMonolithProjectionTest {

    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("inventory").withUsername("inventory")
            .withPassword("inventory");
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
        produce("ofbiz.SHIPMENT", envelope("SHIPMENT", "c",
            Map.of("SHIPMENT_ID", "SH1", "STATUS_ID", "SHIPMENT_INPUT")));
        produce("ofbiz.SHIPMENT_ITEM", envelope("SHIPMENT_ITEM", "c",
            Map.of("SHIPMENT_ID", "SH1", "SHIPMENT_ITEM_SEQ_ID", "00001",
                "PRODUCT_ID", "PRD1", "QUANTITY", "3")));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals("SHIPMENT_INPUT", jdbc.queryForObject(
                "SELECT status_id FROM monolith_shipments WHERE shipment_id='SH1'",
                String.class));
            assertEquals("3", jdbc.queryForObject(
                "SELECT quantity FROM monolith_shipment_items "
                + "WHERE shipment_id='SH1' AND shipment_item_seq_id='00001'",
                String.class));
        });

        // status transition mirrors
        produce("ofbiz.SHIPMENT", envelope("SHIPMENT", "u",
            Map.of("SHIPMENT_ID", "SH1", "STATUS_ID", "SHIPMENT_PACKED")));
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
            assertEquals("SHIPMENT_PACKED", jdbc.queryForObject(
                "SELECT status_id FROM monolith_shipments WHERE shipment_id='SH1'",
                String.class)));

        // duplicate delivery of the same event id — no re-apply
        assertFalse(projectionService.apply("ofbiz.SHIPMENT:0:0",
            envelope("SHIPMENT", "u", Map.of("SHIPMENT_ID", "SH1",
                "STATUS_ID", "SHIPMENT_CANCELLED"))));
        assertEquals("SHIPMENT_PACKED", jdbc.queryForObject(
            "SELECT status_id FROM monolith_shipments WHERE shipment_id='SH1'",
            String.class));

        // rebuild from scratch: wipe tables + idempotency markers, re-consume
        jdbc.execute("DELETE FROM monolith_shipment_items");
        jdbc.execute("DELETE FROM monolith_shipments");
        jdbc.execute("DELETE FROM processed_events");
        produce("ofbiz.SHIPMENT", envelope("SHIPMENT", "c",
            Map.of("SHIPMENT_ID", "SH1", "STATUS_ID", "SHIPMENT_INPUT")));
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
            assertEquals("SHIPMENT_INPUT", jdbc.queryForObject(
                "SELECT status_id FROM monolith_shipments WHERE shipment_id='SH1'",
                String.class)));
        assertEquals(1, count("monolith_shipments", ""));
    }

    @Test
    void shipmentDeleteCascadesItems() {
        produce("ofbiz.SHIPMENT", envelope("SHIPMENT", "c",
            Map.of("SHIPMENT_ID", "SH9", "STATUS_ID", "SHIPMENT_INPUT")));
        produce("ofbiz.SHIPMENT_ITEM", envelope("SHIPMENT_ITEM", "c",
            Map.of("SHIPMENT_ID", "SH9", "SHIPMENT_ITEM_SEQ_ID", "00001",
                "PRODUCT_ID", "PRD9", "QUANTITY", "1")));
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
            assertEquals(1, count("monolith_shipment_items", "WHERE shipment_id='SH9'")));

        produce("ofbiz.SHIPMENT", envelope("SHIPMENT", "d",
            Map.of("SHIPMENT_ID", "SH9", "STATUS_ID", "SHIPMENT_INPUT")));
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(0, count("monolith_shipments", "WHERE shipment_id='SH9'"));
            assertEquals(0, count("monolith_shipment_items", "WHERE shipment_id='SH9'"));
        });
    }
}
