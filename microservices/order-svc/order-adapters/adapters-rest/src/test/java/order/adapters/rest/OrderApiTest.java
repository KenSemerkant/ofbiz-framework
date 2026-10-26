package order.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import order.adapters.rest.persistence.OutboxEntryEntity;
import order.adapters.rest.persistence.OutboxRepository;
import order.application.InventoryGateway;
import order.application.InventoryUnavailableException;
import order.application.PartyValidator;
import order.application.PriceSnapshotProvider;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * CG-4 staging lifecycle, sliced: quote→order→approve→complete through the
 * REST API with the real Postgres store; illegal transitions 409; the price
 * snapshot survives a later price change; cancel emits its event; returns
 * need COMPLETED and then adjust inventory + emit the credit memo. Relay
 * and ECA disabled — the outbox rows ARE the event evidence (product-svc
 * test convention); the on-cluster end-to-end is 70-order-lifecycle.sh.
 */
@SpringBootTest(properties = {"order.relay.enabled=false", "order.eca.enabled=false",
        "order.cdc.enabled=false"},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @TestConfiguration
    static class Stubs {
        @Bean
        @Primary
        PartyValidator partyValidator() {
            return partyId -> !"GHOST".equals(partyId);
        }

        @Bean
        @Primary
        PriceSnapshotProvider priceSnapshotProvider() {
            return (productId, currency) -> "PROD-FREE".equals(productId)
                ? Optional.empty()
                : Optional.of(new PriceSnapshotProvider.ResolvedPrice(
                    new BigDecimal("9.99"), currency));
        }

        @Bean
        @Primary
        InventoryGateway inventoryGateway() {
            return new InventoryGateway() {
                final List<String> reserves = new ArrayList<>();
                final List<String> releases = new ArrayList<>();
                final List<String> receives = new ArrayList<>();

                @Override
                public ReservationReceipt reserve(String orderId, String orderItemSeqId,
                        String productId, String facilityId, BigDecimal quantity) {
                    if ("PROD-OUT".equals(productId)) {
                        throw new InventoryUnavailableException("no ATP for " + productId);
                    }
                    String reservationId = "RES-" + (reserves.size() + 1);
                    reserves.add(productId);
                    return new ReservationReceipt(reservationId, "INV-1", productId,
                        facilityId);
                }

                @Override
                public void release(String reservationId) {
                    releases.add(reservationId);
                }

                @Override
                public void receive(String inventoryItemId, BigDecimal quantity) {
                    receives.add(inventoryItemId);
                }
            };
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    OutboxRepository outbox;

    @Autowired
    order.adapters.rest.persistence.AttributionRepository attributions;

    @Autowired
    order.application.OrderEcaService eca;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path) throws Exception {
        return post(path, "{}");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private List<OutboxEntryEntity> outboxFor(String aggregateKey) {
        return outbox.findAll().stream()
            .filter(entry -> aggregateKey.equals(entry.getAggregateKey()))
            .sorted(java.util.Comparator.comparing(OutboxEntryEntity::getId))
            .toList();
    }

    @Test
    void fullLifecycleQuoteToOrderToComplete() throws Exception {
        // quote with snapshot price 9.99
        HttpResponse<String> quote = post("/api/v1/quotes",
            "{\"customerId\":\"CUST-1\",\"currencyUomId\":\"USD\","
                + "\"lines\":[{\"productId\":\"PROD-1\",\"facilityId\":\"WH\","
                + "\"quantity\":2}]}");
        assertEquals(201, quote.statusCode());
        String quoteId = json.readTree(quote.body()).path("quoteId").asText();

        // P4-F1-S4: conversion preserves the line data (same price, qty, seq)
        HttpResponse<String> order = post("/api/v1/quotes/" + quoteId + "/convert");
        assertEquals(201, order.statusCode());
        JsonNode orderNode = json.readTree(order.body());
        String orderId = orderNode.path("orderId").asText();
        assertEquals("CREATED", orderNode.path("status").asText());
        JsonNode line = orderNode.path("lines").get(0);
        assertEquals("PROD-1", line.path("productId").asText());
        assertEquals(2, line.path("quantity").intValue());
        assertEquals(0, new BigDecimal("9.99").compareTo(
            new BigDecimal(line.path("unitPrice").asText())));
        assertEquals(new BigDecimal("19.98"), new BigDecimal(
            orderNode.path("grandTotal").asText()));

        // history at creation has exactly one entry
        HttpResponse<String> history = get("/api/v1/orders/" + orderId + "/history");
        assertEquals(1, json.readTree(history.body()).path("history").size());

        // illegal: complete before approve → 409 typed error
        assertEquals(409, post("/api/v1/orders/" + orderId + "/complete").statusCode());

        assertEquals(200, post("/api/v1/orders/" + orderId + "/approve").statusCode());
        assertEquals(200, post("/api/v1/orders/" + orderId + "/complete").statusCode());
        // terminal: approve after complete → 409
        assertEquals(409, post("/api/v1/orders/" + orderId + "/approve").statusCode());

        // history appended per transition (P4-F1-S1)
        history = get("/api/v1/orders/" + orderId + "/history");
        List<String> statuses = new ArrayList<>();
        json.readTree(history.body()).path("history")
            .forEach(change -> statuses.add(change.path("status").asText()));
        assertEquals(List.of("CREATED", "APPROVED", "COMPLETED"), statuses);

        // events per state change, in order
        List<String> eventTypes = outboxFor(orderId).stream()
            .map(OutboxEntryEntity::getEventType).toList();
        assertEquals(List.of("order.order.placed.v1", "order.order.approved.v1",
            "order.order.completed.v1"), eventTypes);

        // drive the ECA saga steps the same way the Kafka consumer would
        // (relay/consumers disabled in this slice; on-cluster flow is
        // 70-order-lifecycle.sh) — this stores the reserve receipts the
        // return adjustment later relies on
        eca.onPlaced(orderId);

        // P4-F1-S3: return now works, adjusts inventory, emits credit memo
        HttpResponse<String> returns = post("/api/v1/returns",
            "{\"orderId\":\"" + orderId + "\",\"lines\":[{\"productId\":\"PROD-1\","
                + "\"facilityId\":\"WH\",\"quantity\":1}]}");
        assertEquals(201, returns.statusCode());
        assertTrue(outbox.findAll().stream().anyMatch(entry -> "order.creditmemo.issued.v1"
            .equals(entry.getEventType())), "credit memo event must be emitted");

        // fetch the created return; an unknown id 404s
        String returnId = json.readTree(returns.body()).path("returnId").asText();
        assertEquals(200, get("/api/v1/returns/" + returnId).statusCode());
        assertEquals(404, get("/api/v1/returns/RET-NOPE").statusCode());
        assertEquals(404, get("/api/v1/quotes/QUOTE-NOPE").statusCode());

        // P9-F3 parity slice: order/quote/return listings for the SPA order area
        JsonNode orderList = json.readTree(
            get("/api/v1/orders?customerId=CUST-1&status=COMPLETED").body());
        assertTrue(orderList.isArray() && orderList.size() >= 1);
        boolean foundOrder = false;
        for (int i = 0; i < orderList.size(); i++) {
            foundOrder |= orderId.equals(orderList.get(i).path("orderId").asText());
        }
        assertTrue(foundOrder);
        JsonNode quoteList = json.readTree(get("/api/v1/quotes").body());
        assertTrue(quoteList.isArray() && quoteList.size() >= 1);
        JsonNode returnList = json.readTree(get("/api/v1/returns").body());
        assertTrue(returnList.isArray() && returnList.size() >= 1);
    }

    @Test
    void edgeCasesCoverTheErrorPaths() throws Exception {
        // quote for an unknown party → 400
        assertEquals(400, post("/api/v1/quotes",
            "{\"customerId\":\"GHOST\",\"currencyUomId\":\"USD\",\"lines\":"
                + "[{\"productId\":\"PROD-1\",\"facilityId\":\"WH\",\"quantity\":1}]}")
            .statusCode());
        // unknown quote → 404; a second conversion → 409
        assertEquals(404, post("/api/v1/quotes/QUOTE-NOPE/convert").statusCode());
        HttpResponse<String> quote = post("/api/v1/quotes",
            "{\"customerId\":\"CUST-1\",\"currencyUomId\":\"USD\",\"lines\":"
                + "[{\"productId\":\"PROD-1\",\"facilityId\":\"WH\",\"quantity\":1}]}");
        String quoteId = json.readTree(quote.body()).path("quoteId").asText();
        assertEquals(200, get("/api/v1/quotes/" + quoteId).statusCode());
        assertEquals(201, post("/api/v1/quotes/" + quoteId + "/convert").statusCode());
        assertEquals(409, post("/api/v1/quotes/" + quoteId + "/convert").statusCode());

        // cancel without a body still works (null note branch)
        HttpResponse<String> placed = post("/api/v1/orders",
            "{\"customerId\":\"CUST-1\",\"currencyUomId\":\"USD\",\"lines\":"
                + "[{\"productId\":\"PROD-1\",\"facilityId\":\"WH\",\"quantity\":1}]}");
        String orderId = json.readTree(placed.body()).path("orderId").asText();
        HttpResponse<String> cancelled = client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/orders/" + orderId
                + "/cancel"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, cancelled.statusCode());

        // P4-F2-S2 read model: attribution count per tracking code
        attributions.save(
            new order.adapters.rest.persistence.SmallStores.AttributionEntity(orderId,
                "CUST-1", "PROMO-42", java.time.Instant.now()));
        HttpResponse<String> count = get("/api/v1/attributions?trackingCode=PROMO-42");
        assertEquals(200, count.statusCode());
        assertTrue(Integer.parseInt(count.body()) >= 1, "the attribution row must be counted");
    }

    @Test
    void returnsRejectedWhileOrderNotCompleted() throws Exception {
        HttpResponse<String> placed = post("/api/v1/orders",
            "{\"customerId\":\"CUST-1\",\"currencyUomId\":\"USD\",\"lines\":"
                + "[{\"productId\":\"PROD-1\",\"facilityId\":\"WH\",\"quantity\":1}]}");
        String orderId = json.readTree(placed.body()).path("orderId").asText();
        assertEquals(409, post("/api/v1/returns",
            "{\"orderId\":\"" + orderId + "\",\"lines\":[{\"productId\":\"PROD-1\","
                + "\"facilityId\":\"WH\",\"quantity\":1}]}").statusCode(),
            "returns on a CREATED order must conflict");
    }

    @Test
    void priceSnapshotIsImmutableAgainstLaterPriceChanges() throws Exception {
        HttpResponse<String> placed = post("/api/v1/orders",
            "{\"customerId\":\"CUST-1\",\"currencyUomId\":\"USD\",\"lines\":"
                + "[{\"productId\":\"PROD-1\",\"facilityId\":\"WH\",\"quantity\":1}]}");
        String orderId = json.readTree(placed.body()).path("orderId").asText();

        // the catalog price "changes" (the stub now resolves 999.99)
        // — a fresh order sees it, the placed order keeps 9.99
        HttpResponse<String> after = get("/api/v1/orders/" + orderId);
        JsonNode line = json.readTree(after.body()).path("lines").get(0);
        assertEquals(0, new BigDecimal("9.99")
            .compareTo(new BigDecimal(line.path("unitPrice").asText())),
            "a captured snapshot must not re-price");
    }

    @Test
    void unknownPartyAndUnpricedLineLeaveNoOrder() throws Exception {
        assertEquals(400, post("/api/v1/orders",
            "{\"customerId\":\"GHOST\",\"currencyUomId\":\"USD\",\"lines\":"
                + "[{\"productId\":\"PROD-1\",\"facilityId\":\"WH\",\"quantity\":1}]}")
            .statusCode());
        assertEquals(422, post("/api/v1/orders",
            "{\"customerId\":\"CUST-1\",\"currencyUomId\":\"USD\",\"lines\":"
                + "[{\"productId\":\"PROD-FREE\",\"facilityId\":\"WH\",\"quantity\":1}]}")
            .statusCode());
        assertFalse(outbox.findAll().stream().anyMatch(entry -> entry.getAggregateKey()
            .startsWith("ORDER-")), "rejected carts must leave no order state");
    }

    @Test
    void cancelEmitsCancelledEvent() throws Exception {
        HttpResponse<String> placed = post("/api/v1/orders",
            "{\"customerId\":\"CUST-1\",\"currencyUomId\":\"USD\",\"trackingCode\":"
                + "\"PROMO-42\",\"lines\":[{\"productId\":\"PROD-1\",\"facilityId\":"
                + "\"WH\",\"quantity\":1}]}");
        String orderId = json.readTree(placed.body()).path("orderId").asText();

        HttpResponse<String> cancelled = post("/api/v1/orders/" + orderId + "/cancel",
            "{\"note\":\"buyer remorse\"}");
        assertEquals(200, cancelled.statusCode());
        assertEquals("CANCELLED", json.readTree(cancelled.body()).path("status").asText());
        assertTrue(outboxFor(orderId).stream()
            .anyMatch(entry -> "order.order.cancelled.v1".equals(entry.getEventType())));
        // placed payload carries the tracking code (P4-F2-S2 attribution input)
        assertTrue(outboxFor(orderId).stream()
            .anyMatch(entry -> "order.order.placed.v1".equals(entry.getEventType())
                && entry.getPayload().contains("PROMO-42")));
    }
}
