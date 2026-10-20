package identity.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * P1-F1-S3: `party.party.*` events land in the local projection exactly once,
 * including after a consumer restart + replay of the same event id.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PartyProjectionConsumerTest {

    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("identity")
            .withUsername("identity")
            .withPassword("identity");
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
        registry.add("spring.flyway.locations",
            () -> "classpath:db/migration,classpath:db/testdata");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("identity.relay.enabled", () -> "false");
        registry.add("party.consumer.enabled", () -> "true");
    }

    private static final String EVENT = """
        {"event_id":"evt-party-1","party_id":"party-1","type":"party.party.created",
         "payload":{"partyId":"party-1"}}
        """;

    private final KafkaTemplate<Object, Object> kafka;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private KafkaListenerEndpointRegistry listeners;

    PartyProjectionConsumerTest(@Autowired ProducerFactory<Object, Object> producerFactory) {
        this.kafka = new KafkaTemplate<>(producerFactory);
    }

    @Test
    void projectsOnceAndIgnoresReplayAfterRestart() throws Exception {
        kafka.send("party.party.created.v1", EVENT).get(30, TimeUnit.SECONDS);
        awaitCount("party_projection", 1);

        // AC: restart the consumer, replay the same event id — zero duplicates.
        var container = listeners.getListenerContainer("identity-party-projection");
        assertTrue(container != null, "projection listener must be registered");
        container.stop();
        kafka.send("party.party.created.v1", EVENT).get(30, TimeUnit.SECONDS);
        container.start();

        Thread.sleep(3_000);
        assertEquals(1, count("party_projection"), "replayed event must not duplicate projection rows");
        assertEquals(1, count("processed_events"), "event id must be recorded exactly once");
    }

    private void awaitCount(String table, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            if (count(table) >= expected) {
                return;
            }
            Thread.sleep(250);
        }
        assertEquals(expected, count(table), table + " did not reach expected row count");
    }

    private int count(String table) {
        Integer count = jdbc.queryForObject("select count(*) from " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
