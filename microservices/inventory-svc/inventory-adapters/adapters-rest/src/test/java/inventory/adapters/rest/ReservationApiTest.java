package inventory.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import inventory.adapters.rest.persistence.OutboxEntryEntity;
import inventory.adapters.rest.persistence.OutboxRepository;

/**
 * AC (P3-F2-S2): reserve→confirm/cancel flows via REST with the matching
 * `inventory.stock.reserved/confirmed/released` events committed in the same
 * transaction; cancel is the compensation (ATP comes back).
 */
@SpringBootTest(properties = "inventory.relay.enabled=false",
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ReservationApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @LocalServerPort
    int port;

    @Autowired
    OutboxRepository outbox;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getItem(String itemId) throws Exception {
        return client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/inventory-items/" + itemId))
            .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private List<OutboxEntryEntity> outboxFor(String aggregateKey) {
        return outbox.findAll().stream()
            .filter(e -> aggregateKey.equals(e.getAggregateKey()))
            .sorted(Comparator.comparing(OutboxEntryEntity::getId))
            .toList();
    }

    /** Item "RSV-<n>" holds 10 of WIDGET at WH1 after this. */
    private void stocked(String itemId) throws Exception {
        assertEquals(201, post("/api/v1/inventory-items",
            "{\"inventoryItemId\":\"" + itemId + "\",\"productId\":\"WIDGET\","
                + "\"facilityId\":\"WH1\"}").statusCode());
        assertEquals(200, post("/api/v1/inventory-items/" + itemId + "/receive",
            "{\"quantity\":10}").statusCode());
    }

    private String reserve(String itemId, String orderId, String qty) throws Exception {
        HttpResponse<String> response = post("/api/v1/reservations",
            "{\"orderId\":\"" + orderId + "\",\"orderItemSeqId\":\"00001\","
                + "\"productId\":\"WIDGET\",\"facilityId\":\"WH1\",\"quantity\":" + qty + "}");
        assertEquals(201, response.statusCode());
        tools.jackson.databind.JsonNode body =
            new tools.jackson.databind.ObjectMapper().readTree(response.body());
        assertTrue(body.path("inventoryItemId").asText().equals(itemId),
            "reserve must pick item " + itemId);
        return body.path("reservationId").asText();
    }

    @Test
    void reserveConfirmSagaEmitsEventsAndMovesTotals() throws Exception {
        stocked("RSV-1");

        String reservationId = reserve("RSV-1", "ORD-1", "4");
        // reserve drew ATP only: QOH 10, ATP 6
        assertTrue(getItem("RSV-1").body().contains("\"quantityOnHandTotal\":10"));
        assertTrue(getItem("RSV-1").body().contains("\"availableToPromiseTotal\":6"));
        assertEquals("inventory.stock.reserved.v1", outboxFor(reservationId).get(0).getEventType());
        assertTrue(outboxFor(reservationId).get(0).getPayload().contains("\"orderId\":\"ORD-1\""));

        assertEquals(200, post("/api/v1/reservations/" + reservationId + "/confirm", "")
            .statusCode());
        // confirm shipped the goods: QOH 6, ATP still 6
        assertTrue(getItem("RSV-1").body().contains("\"quantityOnHandTotal\":6"));
        assertTrue(getItem("RSV-1").body().contains("\"availableToPromiseTotal\":6"));
        assertEquals("inventory.stock.confirmed.v1",
            outboxFor(reservationId).get(outboxFor(reservationId).size() - 1).getEventType());

        // one-shot: confirming again is a conflict
        assertEquals(409, post("/api/v1/reservations/" + reservationId + "/confirm", "")
            .statusCode());
    }

    @Test
    void cancelCompensatesByReleasingAtp() throws Exception {
        stocked("RSV-2");

        String reservationId = reserve("RSV-2", "ORD-2", "4");
        assertEquals(200, post("/api/v1/reservations/" + reservationId + "/cancel", "")
            .statusCode());
        assertTrue(getItem("RSV-2").body().contains("\"quantityOnHandTotal\":10"));
        assertTrue(getItem("RSV-2").body().contains("\"availableToPromiseTotal\":10"));
        assertEquals("inventory.stock.released.v1",
            outboxFor(reservationId).get(outboxFor(reservationId).size() - 1).getEventType());

        // released reservations cannot transition again
        assertEquals(409, post("/api/v1/reservations/" + reservationId + "/cancel", "")
            .statusCode());
        assertEquals(409, post("/api/v1/reservations/" + reservationId + "/confirm", "")
            .statusCode());
    }

    @Test
    void reserveGuardsAndErrors() throws Exception {
        stocked("RSV-3");

        // more than ATP: 409
        assertEquals(409, post("/api/v1/reservations",
            "{\"orderId\":\"ORD-X\",\"orderItemSeqId\":\"00001\",\"productId\":\"WIDGET\","
                + "\"facilityId\":\"WH1\",\"quantity\":11}").statusCode());
        // no stock at all for this product/facility: 404
        assertEquals(404, post("/api/v1/reservations",
            "{\"orderId\":\"ORD-X\",\"orderItemSeqId\":\"00001\",\"productId\":\"GIZMO\","
                + "\"facilityId\":\"WH1\",\"quantity\":1}").statusCode());
        // malformed: 400
        assertEquals(400, post("/api/v1/reservations",
            "{\"orderId\":\"ORD-X\",\"orderItemSeqId\":\"00001\",\"productId\":\"WIDGET\","
                + "\"facilityId\":\"WH1\",\"quantity\":0}").statusCode());
        assertEquals(400, post("/api/v1/reservations",
            "{\"orderId\":\" \",\"orderItemSeqId\":\"00001\",\"productId\":\"WIDGET\","
                + "\"facilityId\":\"WH1\",\"quantity\":1}").statusCode());

        // unknown reservation lifecycle ops: 404
        assertEquals(404, post("/api/v1/reservations/nope/confirm", "").statusCode());
        assertEquals(404, post("/api/v1/reservations/nope/cancel", "").statusCode());
        assertEquals(404, client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/reservations/nope"))
            .GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void reservationPayloadEscapesHostileFields() throws Exception {
        assertEquals(201, post("/api/v1/inventory-items",
            "{\"inventoryItemId\":\"RSV-\\\"Q\",\"productId\":\"W\\\"P\",\"facilityId\":\"WH1\"}")
            .statusCode());
        post("/api/v1/inventory-items/RSV-%22Q/receive", "{\"quantity\":10}");

        HttpResponse<String> reserved = post("/api/v1/reservations",
            "{\"orderId\":\"ORD-\\\"Q\",\"orderItemSeqId\":\"00001\",\"productId\":\"W\\\"P\","
                + "\"facilityId\":\"WH1\",\"quantity\":1}");
        assertEquals(201, reserved.statusCode());
        String reservationId =
            new tools.jackson.databind.ObjectMapper().readTree(reserved.body())
                .path("reservationId").asText();

        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        String payload = outboxFor(reservationId).get(0).getPayload();
        assertEquals("ORD-\"Q", mapper.readTree(payload).path("orderId").asText());
        assertEquals(BigDecimal.ONE,
            mapper.readTree(payload).path("quantity").decimalValue());
    }
}
