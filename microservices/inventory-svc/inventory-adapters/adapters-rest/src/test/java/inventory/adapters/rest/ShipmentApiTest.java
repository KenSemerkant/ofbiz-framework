package inventory.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import inventory.adapters.rest.persistence.ReservationRepository;

/**
 * AC (P3-F2-S3): ship-order flow — create→pack→ship transitions statuses
 * (invalid transition → 409), ship() confirms the linked order's
 * reservations and emits `inventory.stock.shipped.v1`.
 */
@SpringBootTest(properties = "inventory.relay.enabled=false",
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ShipmentApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @LocalServerPort
    int port;

    @Autowired
    OutboxRepository outbox;

    @Autowired
    ReservationRepository reservations;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private List<OutboxEntryEntity> outboxFor(String aggregateKey) {
        return outbox.findAll().stream()
            .filter(e -> aggregateKey.equals(e.getAggregateKey()))
            .sorted(Comparator.comparing(OutboxEntryEntity::getId))
            .toList();
    }

    private String stockedAndReserved(String itemId, String orderId, String qty) throws Exception {
        assertEquals(201, post("/api/v1/inventory-items",
            "{\"inventoryItemId\":\"" + itemId + "\",\"productId\":\"WIDGET\","
                + "\"facilityId\":\"WH1\"}").statusCode());
        assertEquals(200, post("/api/v1/inventory-items/" + itemId + "/receive",
            "{\"quantity\":10}").statusCode());
        HttpResponse<String> reserved = post("/api/v1/reservations",
            "{\"orderId\":\"" + orderId + "\",\"orderItemSeqId\":\"00001\","
                + "\"productId\":\"WIDGET\",\"facilityId\":\"WH1\",\"quantity\":" + qty + "}");
        assertEquals(201, reserved.statusCode());
        return new tools.jackson.databind.ObjectMapper().readTree(reserved.body())
            .path("reservationId").asText();
    }

    @Test
    void shipOrderFlowTransitionsStatusesAndShipsStock() throws Exception {
        String reservationId = stockedAndReserved("SHIP-1", "ORD-1", "4");

        HttpResponse<String> created = post("/api/v1/shipments", """
            {"shipmentId":"SH-A","orderId":"ORD-1",
             "items":[{"productId":"WIDGET","quantity":4,"inventoryItemId":"SHIP-1"}]}""");
        assertEquals(201, created.statusCode());
        assertTrue(created.body().contains("\"status\":\"CREATED\""));
        assertTrue(created.body().contains("\"shipmentItemSeqId\":\"00001\""));
        assertEquals("inventory.shipment.updated.v1", outboxFor("SH-A").get(0).getEventType());

        HttpResponse<String> packed = post("/api/v1/shipments/SH-A/pack", "");
        assertEquals(200, packed.statusCode());
        assertTrue(packed.body().contains("\"status\":\"PACKED\""));

        HttpResponse<String> shipped = post("/api/v1/shipments/SH-A/ship", "");
        assertEquals(200, shipped.statusCode());
        assertTrue(shipped.body().contains("\"status\":\"SHIPPED\""));

        // ship() confirmed the linked reservation and shipped the goods:
        // QOH 10-4=6, ATP stays 6 (drawn at reserve), reservation CONFIRMED
        HttpResponse<String> item = get("/api/v1/inventory-items/SHIP-1");
        assertTrue(item.body().contains("\"quantityOnHandTotal\":6"));
        assertTrue(item.body().contains("\"availableToPromiseTotal\":6"));
        assertEquals("CONFIRMED", reservations.findById(reservationId).orElseThrow()
            .getStatus());

        List<String> events = outboxFor("SH-A").stream().map(OutboxEntryEntity::getEventType)
            .toList();
        assertEquals(3, events.stream().filter("inventory.shipment.updated.v1"::equals).count());
        assertEquals("inventory.stock.shipped.v1", events.get(events.size() - 1));
        assertTrue(outboxFor(reservationId).stream()
            .anyMatch(e -> "inventory.stock.confirmed.v1".equals(e.getEventType())));
    }

    @Test
    void invalidTransitionsAreRejectedWithConflict() throws Exception {
        assertEquals(201, post("/api/v1/shipments",
            "{\"shipmentId\":\"SH-B\",\"items\":[{\"productId\":\"WIDGET\",\"quantity\":1,"
                + "\"inventoryItemId\":\"X\"}]}").statusCode());

        // ship skips PACKED: invalid forward transition
        assertEquals(409, post("/api/v1/shipments/SH-B/ship", "").statusCode());
        assertEquals(200, post("/api/v1/shipments/SH-B/pack", "").statusCode());
        // pack twice: invalid
        assertEquals(409, post("/api/v1/shipments/SH-B/pack", "").statusCode());
        assertEquals(200, post("/api/v1/shipments/SH-B/ship", "").statusCode());
        // transitions from SHIPPED are all invalid
        assertEquals(409, post("/api/v1/shipments/SH-B/ship", "").statusCode());
        assertEquals(409, post("/api/v1/shipments/SH-B/pack", "").statusCode());
    }

    @Test
    void shipmentGuardsAndErrors() throws Exception {
        assertEquals(400, post("/api/v1/shipments",
            "{\"shipmentId\":\" \",\"items\":[]}").statusCode());
        assertEquals(400, post("/api/v1/shipments",
            "{\"shipmentId\":\"SH-C\",\"orderId\":\" \"}").statusCode());
        assertEquals(400, post("/api/v1/shipments",
            "{\"shipmentId\":\"SH-C\",\"items\":[{\"productId\":\"P\",\"quantity\":0,"
                + "\"inventoryItemId\":\"I\"}]}").statusCode());
        // duplicate id
        assertEquals(201, post("/api/v1/shipments", "{\"shipmentId\":\"SH-D\"}").statusCode());
        assertEquals(409, post("/api/v1/shipments", "{\"shipmentId\":\"SH-D\"}").statusCode());
        // unknown shipment
        assertEquals(404, get("/api/v1/shipments/nope").statusCode());
        assertEquals(404, post("/api/v1/shipments/nope/pack", "").statusCode());
        assertEquals(404, post("/api/v1/shipments/nope/ship", "").statusCode());
    }

    /**
     * Quality review: a pack computed off a stale (pre-ship) snapshot must
     * never regress SHIPPED -> PACKED. The @Version on shipments makes the
     * stale write fail at flush; after retrying from fresh state the domain
     * guard rejects it. Racing packs and a ship on a CREATED shipment: once a
     * ship succeeds, the final status must still be SHIPPED.
     */
    @Test
    void concurrentPackAndShipCannotRegressStatus() throws Exception {
        for (int round = 0; round < 5; round++) {
            String shipmentId = "SH-RACE-" + round;
            assertEquals(201, post("/api/v1/shipments",
                "{\"shipmentId\":\"" + shipmentId + "\"}").statusCode());

            var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
            var start = new java.util.concurrent.CountDownLatch(1);
            var packs = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 4; i++) {
                packs.add(executor.submit(() -> {
                    start.await();
                    return post("/api/v1/shipments/" + shipmentId + "/pack", "").statusCode();
                }));
            }
            // the ship races the packs; it can only 200 from PACKED state
            java.util.concurrent.Future<Integer> ship = executor.submit(() -> {
                start.await();
                return post("/api/v1/shipments/" + shipmentId + "/ship", "").statusCode();
            });
            start.countDown();
            boolean shipped = ship.get(30, java.util.concurrent.TimeUnit.SECONDS) == 200;
            for (java.util.concurrent.Future<Integer> pack : packs) {
                org.junit.jupiter.api.Assertions.assertNotEquals(500,
                    pack.get(30, java.util.concurrent.TimeUnit.SECONDS),
                    "conflict must map to 409, not 500");
            }
            executor.shutdown();
            String finalStatus = new tools.jackson.databind.ObjectMapper()
                .readTree(get("/api/v1/shipments/" + shipmentId).body())
                .path("status").asText();
            // the regression property: once shipped, always shipped
            if (shipped) {
                org.junit.jupiter.api.Assertions.assertEquals("SHIPPED", finalStatus,
                    "status regressed after a successful ship");
            }
            org.junit.jupiter.api.Assertions.assertTrue(
                finalStatus.equals("PACKED") || finalStatus.equals("SHIPPED"),
                "final status must be a legal forward state, was " + finalStatus);
        }
    }

    /** Quality review: duplicate-id create race → one 201, one 409, one row. */
    @Test
    void duplicateCreateRaceYieldsExactlyOneRow() throws Exception {
        var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        var start = new java.util.concurrent.CountDownLatch(1);
        var results = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
        for (int i = 0; i < 2; i++) {
            results.add(executor.submit(() -> {
                start.await();
                return post("/api/v1/shipments", "{\"shipmentId\":\"SH-DUP-RACE\"}")
                    .statusCode();
            }));
        }
        start.countDown();
        int created = 0;
        int conflicts = 0;
        for (java.util.concurrent.Future<Integer> result : results) {
            int status = result.get(30, java.util.concurrent.TimeUnit.SECONDS);
            if (status == 201) {
                created++;
            } else {
                assertEquals(409, status, "duplicate race must conflict, not " + status);
                conflicts++;
            }
        }
        executor.shutdown();
        assertEquals(1, created);
        assertEquals(1, conflicts);
        assertEquals(200, get("/api/v1/shipments/SH-DUP-RACE").statusCode());
    }

    @Test
    void shipmentPayloadEscapesHostileFields() throws Exception {
        assertEquals(201, post("/api/v1/shipments",
            "{\"shipmentId\":\"SH-\\\\Q\",\"orderId\":\"ORD-\\\"Q\","
                + "\"items\":[{\"productId\":\"W\\\"P\",\"quantity\":1,"
                + "\"inventoryItemId\":\"I\"}]}").statusCode());

        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        String payload = outboxFor("SH-\\Q").get(0).getPayload();
        mapper.readTree(payload); // invalid JSON would throw
        assertEquals("ORD-\"Q", mapper.readTree(payload).path("orderId").asText());
        assertEquals("W\"P", mapper.readTree(payload).path("items").get(0)
            .path("productId").asText());
    }
}
