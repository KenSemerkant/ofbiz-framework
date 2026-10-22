package inventory.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Future;
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
 * AC (P3-F2-S1): receive/adjust/transfer flows via REST with the matching
 * `inventory.stock.*` outbox event committed in the same transaction.
 */
@SpringBootTest(properties = "inventory.relay.enabled=false",
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class InventoryApiTest {

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

    /** Outbox rows for one aggregate in insertion (id) order — findAll() is unordered. */
    private List<OutboxEntryEntity> outboxFor(String aggregateKey) {
        return outbox.findAll().stream()
            .filter(e -> aggregateKey.equals(e.getAggregateKey()))
            .sorted(Comparator.comparing(OutboxEntryEntity::getId))
            .toList();
    }

    @Test
    void receiveFlowRaisesTotalsAndEmitsReceived() throws Exception {
        assertEquals(201, post("/api/v1/inventory-items",
            "{\"inventoryItemId\":\"II1\",\"productId\":\"WIDGET\",\"facilityId\":\"WH1\"}")
            .statusCode());
        assertEquals("inventory.item.created.v1", outboxFor("II1").get(0).getEventType());

        HttpResponse<String> received = post("/api/v1/inventory-items/II1/receive",
            "{\"quantity\":10}");
        assertEquals(200, received.statusCode());
        assertTrue(received.body().contains("\"quantityOnHandTotal\":10"));
        assertTrue(received.body().contains("\"availableToPromiseTotal\":10"));
        assertEquals("inventory.stock.received.v1", outboxFor("II1").get(1).getEventType());
        assertTrue(outboxFor("II1").get(1).getPayload().contains("\"quantity\":10"));

        assertTrue(getItem("II1").body().contains("\"quantityOnHandTotal\":10"));
    }

    @Test
    void adjustFlowMovesBothTotalsAndEmitsAdjusted() throws Exception {
        post("/api/v1/inventory-items",
            "{\"inventoryItemId\":\"II2\",\"productId\":\"WIDGET\",\"facilityId\":\"WH1\"}");
        post("/api/v1/inventory-items/II2/receive", "{\"quantity\":10}");

        HttpResponse<String> adjusted = post("/api/v1/inventory-items/II2/adjust",
            "{\"quantity\":-3}");
        assertEquals(200, adjusted.statusCode());
        assertTrue(adjusted.body().contains("\"quantityOnHandTotal\":7"));
        assertTrue(adjusted.body().contains("\"availableToPromiseTotal\":7"));
        assertEquals("inventory.stock.adjusted.v1",
            outboxFor("II2").get(outboxFor("II2").size() - 1).getEventType());

        // an adjustment below the floor is a conflict, not a bad request
        assertEquals(409, post("/api/v1/inventory-items/II2/adjust", "{\"quantity\":-8}")
            .statusCode());
    }

    @Test
    void transferFlowMovesStockBetweenItemsAndRejectsOverdraft() throws Exception {
        post("/api/v1/inventory-items",
            "{\"inventoryItemId\":\"SRC\",\"productId\":\"WIDGET\",\"facilityId\":\"WH1\"}");
        post("/api/v1/inventory-items",
            "{\"inventoryItemId\":\"DST\",\"productId\":\"WIDGET\",\"facilityId\":\"WH2\"}");
        post("/api/v1/inventory-items/SRC/receive", "{\"quantity\":10}");

        HttpResponse<String> transferred = post("/api/v1/inventory-items/SRC/transfer",
            "{\"toInventoryItemId\":\"DST\",\"quantity\":4}");
        assertEquals(200, transferred.statusCode());
        assertTrue(transferred.body().contains("\"quantityOnHandTotal\":6"));
        assertTrue(getItem("DST").body().contains("\"quantityOnHandTotal\":4"));

        OutboxEntryEntity event = outboxFor("SRC").stream()
            .filter(e -> "inventory.stock.transferred.v1".equals(e.getEventType()))
            .findFirst().orElseThrow();
        assertTrue(event.getPayload().contains("\"to\":{\"inventoryItemId\":\"DST\""));

        // insufficient ATP at the source: 409, target untouched
        assertEquals(409, post("/api/v1/inventory-items/SRC/transfer",
            "{\"toInventoryItemId\":\"DST\",\"quantity\":7}").statusCode());
        assertTrue(getItem("DST").body().contains("\"quantityOnHandTotal\":4"));

        // unknown target: 404
        assertEquals(404, post("/api/v1/inventory-items/SRC/transfer",
            "{\"toInventoryItemId\":\"nope\",\"quantity\":1}").statusCode());
        // transfer onto itself: 400
        assertEquals(400, post("/api/v1/inventory-items/SRC/transfer",
            "{\"toInventoryItemId\":\"SRC\",\"quantity\":1}").statusCode());
    }

    @Test
    void itemGuardsAndErrors() throws Exception {
        assertEquals(400, post("/api/v1/inventory-items",
            "{\"inventoryItemId\":\" \",\"productId\":\"P\",\"facilityId\":\"F\"}")
            .statusCode());
        assertEquals(201, post("/api/v1/inventory-items",
            "{\"inventoryItemId\":\"DUP\",\"productId\":\"P\",\"facilityId\":\"F\"}")
            .statusCode());
        assertEquals(409, post("/api/v1/inventory-items",
            "{\"inventoryItemId\":\"DUP\",\"productId\":\"P\",\"facilityId\":\"F\"}")
            .statusCode());
        assertEquals(404, getItem("nope").statusCode());
        assertEquals(404, post("/api/v1/inventory-items/nope/receive", "{\"quantity\":1}")
            .statusCode());
        assertEquals(400, post("/api/v1/inventory-items/DUP/receive", "{\"quantity\":0}")
            .statusCode());
        assertEquals(400, post("/api/v1/inventory-items/DUP/adjust", "{\"quantity\":0}")
            .statusCode());
    }

    /**
     * Quality review: concurrent stock ops on one item must conflict-and-
     * retry (409/200), never 500 — and no lost updates: every successful
     * receive lands in the totals.
     */
    @Test
    void concurrentReceivesRetryInsteadOfFailingAndLoseNothing() throws Exception {
        assertEquals(201, post("/api/v1/inventory-items",
            "{\"inventoryItemId\":\"RACE\",\"productId\":\"WIDGET\",\"facilityId\":\"WH1\"}")
            .statusCode());

        int threads = 10;
        var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        var start = new java.util.concurrent.CountDownLatch(1);
        var results = new java.util.ArrayList<Future<Integer>>();
        for (int i = 0; i < threads; i++) {
            results.add(executor.submit(() -> {
                start.await();
                Thread.sleep(java.util.concurrent.ThreadLocalRandom.current().nextLong(20));
                return post("/api/v1/inventory-items/RACE/receive", "{\"quantity\":1}")
                    .statusCode();
            }));
        }
        start.countDown();
        int successes = 0;
        for (Future<Integer> result : results) {
            int status = result.get(30, java.util.concurrent.TimeUnit.SECONDS);
            org.junit.jupiter.api.Assertions.assertNotEquals(500, status,
                "optimistic conflict must retry, not 500");
            if (status == 200) {
                successes++;
            }
        }
        executor.shutdown();

        // no lost updates: the totals equal exactly the successful receives.
        // A 409 here is legal behavior (bounded retry exhausted under
        // contention) — what must never happen is a 500 or a lost increment.
        HttpResponse<String> item = getItem("RACE");
        assertTrue(item.body().contains(
            "\"quantityOnHandTotal\":" + successes));
        assertTrue(item.body().contains(
            "\"availableToPromiseTotal\":" + successes));
        assertTrue(successes >= 1, "the herd should overwhelmingly succeed");
        assertEquals(successes, outboxFor("RACE").stream()
            .filter(e -> "inventory.stock.received.v1".equals(e.getEventType())).count());
    }

    /** Quality review: duplicate-id create race → one row, one 201, one 409. */
    @Test
    void duplicateCreateRaceYieldsExactlyOneRowAndEvent() throws Exception {
        var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        var start = new java.util.concurrent.CountDownLatch(1);
        var results = new java.util.ArrayList<Future<Integer>>();
        for (int i = 0; i < 2; i++) {
            results.add(executor.submit(() -> {
                start.await();
                return post("/api/v1/inventory-items",
                    "{\"inventoryItemId\":\"DUP-RACE\",\"productId\":\"P\",\"facilityId\":\"F\"}")
                    .statusCode();
            }));
        }
        start.countDown();
        int created = 0;
        int conflicts = 0;
        for (Future<Integer> result : results) {
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
        assertEquals(1, outboxFor("DUP-RACE").stream()
            .filter(e -> "inventory.item.created.v1".equals(e.getEventType())).count());
    }

    @Test
    void outboxPayloadsAreValidJsonForHostileFields() throws Exception {
        // quotes and backslashes in every user-supplied field
        assertEquals(201, post("/api/v1/inventory-items",
            "{\"inventoryItemId\":\"II\\\"Q\",\"productId\":\"P\\\\X\",\"facilityId\":\"F\\\"K\"}")
            .statusCode());

        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        for (OutboxEntryEntity entry : outboxFor("II\"Q")) {
            mapper.readTree(entry.getPayload()); // invalid JSON would throw
        }
        assertEquals("II\"Q", mapper.readTree(outboxFor("II\"Q").get(0).getPayload())
            .path("inventoryItemId").asText());
        assertEquals("P\\X", mapper.readTree(outboxFor("II\"Q").get(0).getPayload())
            .path("productId").asText());
    }
}
