package audit.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * P1-F3-S1/S2: wildcard ingestion across topics, monthly partition creation,
 * ordered/paginated query API, HIGH-severity alerting and idempotent replay.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuditIngestTest {

    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("audit")
            .withUsername("audit")
            .withPassword("audit");
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
        registry.add("spring.kafka.consumer.group-id", () -> "audit-svc-test");
        registry.add("spring.kafka.consumer.properties.metadata.max.age.ms", () -> "2000");
    }

    private static final List<String> EVENTS = List.of(
        """
        {"eventId":"audit-evt-1","type":"ORDER_CREATED","severity":"INFO",
         "occurredAt":"2026-10-20T10:00:00Z","payload":{"orderId":"o1"}}
        """,
        """
        {"eventId":"audit-evt-2","type":"PARTY_UPDATED","severity":"WARN",
         "occurredAt":"2026-10-20T10:00:01Z","payload":{"partyId":"p1"}}
        """,
        """
        {"eventId":"audit-evt-3","type":"SECRET_ACCESSED","severity":"HIGH",
         "occurredAt":"2026-10-20T10:00:02Z","payload":{"secret":"s1"}}
        """);
    private static final List<String> TOPICS = List.of(
        "demo.order.created.v1", "demo.party.updated.v1", "audit.secret.accessed.v1");

    private final KafkaTemplate<Object, Object> kafka;
    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    AuditIngestTest(@Autowired ProducerFactory<Object, Object> producerFactory) {
        this.kafka = new KafkaTemplate<>(producerFactory);
    }

    @Test
    void ingestsThreeTopicsInOrderWithPartitionPaginationAlertAndIdempotency() throws Exception {
        for (int i = 0; i < EVENTS.size(); i++) {
            kafka.send(TOPICS.get(i), EVENTS.get(i)).get(30, TimeUnit.SECONDS);
        }
        awaitEventCount(3);

        // Ordered query across all three topics.
        JsonNode all = query("/api/v1/audit-events?from=2026-10-20T00:00:00Z&to=2026-10-21T00:00:00Z&size=10");
        assertEquals(3, all.path("total").asInt());
        List<String> ids = new java.util.ArrayList<>();
        for (JsonNode item : all.path("items")) {
            ids.add(item.path("eventId").asText());
        }
        assertEquals(List.of("audit-evt-1", "audit-evt-2", "audit-evt-3"), ids,
            "events must be returned in business-time order");

        // Pagination envelope.
        JsonNode page = query("/api/v1/audit-events?from=2026-10-20T00:00:00Z&to=2026-10-21T00:00:00Z&page=1&size=1");
        assertEquals(3, page.path("total").asInt());
        assertEquals(1, page.path("items").size());
        assertEquals("audit-evt-2", page.path("items").get(0).path("eventId").asText());

        // Monthly partition for the business month.
        assertEquals(1, jdbc.queryForObject(
            "select count(*) from pg_class where relname = 'audit_events_y2026m10'", Integer.class));

        // HIGH severity raised an alert on notification.requested.v1.
        assertTrue(AlertProbe.RECEIVED.await(30, TimeUnit.SECONDS), "no alert published");
        JsonNode alert = json.readTree(AlertProbe.PAYLOADS.get(0));
        assertEquals("audit-evt-3", alert.path("eventId").asText());
        assertEquals("HIGH", alert.path("severity").asText());

        // Replay of a known event id is a no-op.
        kafka.send(TOPICS.get(0), EVENTS.get(0)).get(30, TimeUnit.SECONDS);
        Thread.sleep(2_000);
        assertEquals(3, count());
    }

    @TestConfiguration
    static class AlertProbe {
        static final CountDownLatch RECEIVED = new CountDownLatch(1);
        static final List<String> PAYLOADS = new CopyOnWriteArrayList<>();

        @Bean
        AlertListener alertListener() {
            return new AlertListener();
        }
    }

    static class AlertListener {
        @KafkaListener(topics = KafkaAlertPublisher.ALERT_TOPIC, groupId = "alert-probe")
        public void onAlert(String payload) {
            AlertProbe.PAYLOADS.add(payload);
            AlertProbe.RECEIVED.countDown();
        }
    }

    private JsonNode query(String path) throws Exception {
        HttpResponse<String> response = http.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "query failed: " + response.body());
        return json.readTree(response.body());
    }

    private void awaitEventCount(int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        while (System.nanoTime() < deadline) {
            if (count() >= expected) {
                return;
            }
            Thread.sleep(250);
        }
        assertEquals(expected, count(), "audit events were not consumed");
    }

    private int count() {
        Integer count = jdbc.queryForObject("select count(*) from audit_events", Integer.class);
        return count == null ? 0 : count;
    }

    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    private int port;
}
