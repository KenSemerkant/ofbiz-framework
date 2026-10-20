package identity.adapters.rest;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for identity integration tests: one shared Postgres container
 * (Flyway migrates the credential/outbox schema), Kafka features OFF by
 * default so non-Kafka tests stay fast and quiet. Kafka-based tests
 * override the properties in their own @DynamicPropertySource.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IdentityIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("identity")
        .withUsername("identity")
        .withPassword("identity");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations",
            () -> "classpath:db/migration,classpath:db/testdata");
        registry.add("identity.relay.enabled", () -> "false");
        registry.add("party.consumer.enabled", () -> "false");
    }

    @LocalServerPort
    protected int port;
}
