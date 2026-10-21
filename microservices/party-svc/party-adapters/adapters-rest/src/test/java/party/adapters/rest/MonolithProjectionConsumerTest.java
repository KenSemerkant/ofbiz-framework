package party.adapters.rest;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import party.adapters.rest.persistence.MonolithPartyRepository;
import party.application.MonolithProjectionService;

/**
 * P2-F2-S2 AC: the monolith CDC projection is rebuilt from scratch via
 * re-consume, and every event applies exactly once (topic:partition:offset
 * idempotency).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"party.relay.enabled=false", "party.cdc.enabled=true",
                "party.grpc.port=0"})
class MonolithProjectionConsumerTest {

    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("party").withUsername("party").withPassword("party");
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
    MonolithPartyRepository projections;

    @Autowired
    MonolithProjectionService projectionService;

    @Autowired
    JdbcTemplate jdbc;

    private static String envelope(String table, String op, Map<String, String> after) {
        var fields = new StringBuilder();
        after.forEach((k, v) -> fields.append('"').append(k).append("\":")
            .append(v == null ? "null" : "\"" + v + "\"").append(','));
        fields.setLength(fields.length() - 1);
        return "{\"op\":\"" + op + "\",\"before\":null,\"after\":{" + fields
            + "},\"source\":{\"table\":\"" + table + "\"}}";
    }

    private void produce(String topic, String payload) {
        kafka.send(topic, payload).join();
    }

    @Test
    void cdcEventsProjectAndReplayIsIdempotent() {
        produce("ofbiz.PARTY", envelope("PARTY", "c",
            Map.of("PARTY_ID", "P1", "PARTY_TYPE_ID", "PERSON")));
        produce("ofbiz.PERSON", envelope("PERSON", "c",
            Map.of("PARTY_ID", "P1", "FIRST_NAME", "Ada", "LAST_NAME", "Lovelace")));
        produce("ofbiz.PARTY_ROLE", envelope("PARTY_ROLE", "c",
            Map.of("PARTY_ID", "P1", "ROLE_TYPE_ID", "CUSTOMER")));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            var party = projections.findById("P1").orElseThrow();
            assertEquals("PERSON", party.getPartyTypeId());
            assertEquals("Ada", party.getFirstName());
            assertTrue(party.getRoles().contains("CUSTOMER"));
        });

        // duplicate delivery of the same event id (PERSON topic offset 0) — no re-apply
        assertFalse(projectionService.apply("ofbiz.PERSON:0:0",
            envelope("PERSON", "c", Map.of("PARTY_ID", "P1", "FIRST_NAME", "X",
                "LAST_NAME", "Y"))));
        assertEquals("Ada", projections.findById("P1").orElseThrow().getFirstName());

        // rebuild from scratch: wipe tables + idempotency markers, re-consume
        jdbc.execute("DELETE FROM monolith_party_roles");
        jdbc.execute("DELETE FROM monolith_parties");
        jdbc.execute("DELETE FROM processed_events");
        produce("ofbiz.PARTY", envelope("PARTY", "c",
            Map.of("PARTY_ID", "P1", "PARTY_TYPE_ID", "PERSON")));
        produce("ofbiz.PERSON", envelope("PERSON", "c",
            Map.of("PARTY_ID", "P1", "FIRST_NAME", "Ada", "LAST_NAME", "Lovelace")));
        produce("ofbiz.PARTY_ROLE", envelope("PARTY_ROLE", "c",
            Map.of("PARTY_ID", "P1", "ROLE_TYPE_ID", "CUSTOMER")));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            var party = projections.findById("P1").orElseThrow();
            assertEquals("Ada", party.getFirstName());
            assertTrue(party.getRoles().contains("CUSTOMER"));
        });
        assertEquals(1, projections.count());
    }
}
