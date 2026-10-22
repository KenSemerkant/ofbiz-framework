package product.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import product.adapters.rest.persistence.ProductRepository;
import product.application.OutboxWriter;

/**
 * ADR-009 atomicity (P3-F1 spec review): the state change and its outbox row
 * commit in ONE transaction — when the outbox append fails, the product write
 * must roll back (no state change without event).
 */
@SpringBootTest(properties = "product.relay.enabled=false",
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OutboxAtomicityTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @TestConfiguration
    static class FailingOutbox {
        @Bean
        @Primary
        OutboxWriter failingOutboxWriter() {
            return (eventType, aggregateKey, payload) -> {
                throw new IllegalStateException("outbox down");
            };
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    ProductRepository products;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void outboxFailureRollsBackTheProductWrite() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/products"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                "{\"productId\":\"TXN1\",\"productTypeId\":\"FINISHED_GOOD\","
                    + "\"internalName\":\"Txn\"}"))
            .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(500, response.statusCode());
        assertFalse(products.existsById("TXN1"), "product write must roll back with the outbox");
    }
}
