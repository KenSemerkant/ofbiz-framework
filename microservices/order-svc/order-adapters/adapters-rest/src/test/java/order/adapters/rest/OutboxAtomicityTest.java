package order.adapters.rest;

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
import order.adapters.rest.persistence.OrderRepository;
import order.application.OutboxWriter;
import order.application.PartyValidator;
import order.application.PriceSnapshotProvider;

/**
 * ADR-009 atomicity (product-svc convention): the order write and its
 * outbox row commit in ONE transaction — when the outbox append fails, the
 * order write must roll back.
 */
@SpringBootTest(properties = {"order.relay.enabled=false", "order.eca.enabled=false",
        "order.cdc.enabled=false"},
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

        @Bean
        @Primary
        PartyValidator partyValidator() {
            return partyId -> true;
        }

        @Bean
        @Primary
        PriceSnapshotProvider priceSnapshotProvider() {
            return (productId, currency) -> java.util.Optional.of(
                new PriceSnapshotProvider.ResolvedPrice(java.math.BigDecimal.TEN, "USD"));
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    OrderRepository orders;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void outboxFailureRollsBackTheOrderWrite() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/orders"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                "{\"customerId\":\"CUST-TXN\",\"currencyUomId\":\"USD\",\"lines\":"
                    + "[{\"productId\":\"PROD-1\",\"facilityId\":\"WH\",\"quantity\":1}]}"))
            .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(500, response.statusCode());
        assertFalse(orders.findAll().stream()
            .anyMatch(order -> "CUST-TXN".equals(order.getCustomerId())),
            "order write must roll back with the outbox");
    }
}
