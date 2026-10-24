package manufacturing.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import manufacturing.adapters.rest.persistence.OutboxRepository;
import manufacturing.application.MrpEventStore;
import manufacturing.domain.MrpEvent;
import manufacturing.domain.MrpEventType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * CG-6 slice: BOM and routing CRUD with typed 409/404 semantics, then the
 * deterministic MRP run over a seeded demand/supply ledger — proposals
 * must equal demand minus supply per product, one outbox
 * manufacturing.mrp.proposed.v1 row per short product. Relay and the
 * event consumers are disabled — the outbox rows ARE the event evidence
 * (order-svc test convention); on-cluster is the gate drill.
 */
@SpringBootTest(properties = {"manufacturing.relay.enabled=false",
        "manufacturing.mrp.enabled=false"}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ManufacturingApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @LocalServerPort
    int port;

    @Autowired
    OutboxRepository outbox;

    @Autowired
    MrpEventStore eventStore;

    final HttpClient http = HttpClient.newHttpClient();
    final ObjectMapper json = new ObjectMapper();

    private JsonNode post(String path, String body) throws Exception {
        return json.readTree(send("POST", path, body));
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:%d%s".formatted(port, path))).GET().build();
        return json.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
    }

    private String send(String method, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:%d%s".formatted(port, path)))
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private int status(String method, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:%d%s".formatted(port, path)))
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test
    void bomCrudWithTypedConflictsAndNotFounds() throws Exception {
        String bom = """
            {"productId":"P-BOM","components":[
              {"productId":"COMP-1","quantity":2,"scrapPct":5},
              {"productId":"COMP-2","quantity":1,"scrapPct":0}]}
            """;
        post("/api/v1/boms", bom);
        assertTrue(outbox.findAll().stream()
            .anyMatch(e -> "manufacturing.bom.created.v1".equals(e.getEventType())));
        assertEquals(409, status("POST", "/api/v1/boms", bom), "duplicate create conflicts");

        JsonNode fetched = get("/api/v1/boms/P-BOM");
        assertEquals("P-BOM", fetched.path("productId").asString());
        assertEquals(2, fetched.path("components").size());
        assertEquals(2, fetched.path("components").get(0).path("quantity").asInt());

        post("/api/v1/boms", """
            {"productId":"P-BOM2","components":[{"productId":"COMP-1","quantity":3,"scrapPct":0}]}
            """);
        String updated = """
            {"productId":"P-BOM2","components":[{"productId":"COMP-9","quantity":4,"scrapPct":0}]}
            """;
        JsonNode afterUpdate = json.readTree(send("PUT", "/api/v1/boms/P-BOM2", updated));
        assertEquals("COMP-9", afterUpdate.path("components").get(0).path("productId").asString());
        assertTrue(outbox.findAll().stream()
            .anyMatch(e -> "manufacturing.bom.updated.v1".equals(e.getEventType())));
        assertEquals(404, status("PUT", "/api/v1/boms/P-NONE", updated));

        assertEquals(204, status("DELETE", "/api/v1/boms/P-BOM2", ""));
        assertTrue(outbox.findAll().stream()
            .anyMatch(e -> "manufacturing.bom.deleted.v1".equals(e.getEventType())));
        assertEquals(404, status("GET", "/api/v1/boms/P-BOM2", ""));
        assertEquals(404, status("DELETE", "/api/v1/boms/P-BOM2", ""));
    }

    @Test
    void routingCrudWithTypedConflictsAndNotFounds() throws Exception {
        String routing = """
            {"routingId":"RT-1","productId":"P-BOM","tasks":[
              {"seq":1,"name":"CUT","estimatedDuration":10},
              {"seq":2,"name":"ASSEMBLE","estimatedDuration":30}]}
            """;
        post("/api/v1/routings", routing);
        assertTrue(outbox.findAll().stream()
            .anyMatch(e -> "manufacturing.routing.created.v1".equals(e.getEventType())));
        assertEquals(409, status("POST", "/api/v1/routings", routing));

        JsonNode fetched = get("/api/v1/routings/RT-1");
        assertEquals("RT-1", fetched.path("routingId").asString());
        assertEquals("P-BOM", fetched.path("productId").asString());
        assertEquals(30, fetched.path("tasks").get(1).path("estimatedDuration").asLong());

        JsonNode afterUpdate = json.readTree(send("PUT", "/api/v1/routings/RT-1", """
            {"routingId":"RT-1","productId":"P-BOM",
             "tasks":[{"seq":1,"name":"WELD","estimatedDuration":20}]}
            """));
        assertEquals("WELD", afterUpdate.path("tasks").get(0).path("name").asString());
        assertTrue(outbox.findAll().stream()
            .anyMatch(e -> "manufacturing.routing.updated.v1".equals(e.getEventType())));
        assertEquals(404, status("GET", "/api/v1/routings/RT-404", ""));
        assertEquals(404, status("PUT", "/api/v1/routings/RT-404", """
            {"routingId":"RT-404","productId":"P",
             "tasks":[{"seq":1,"name":"X","estimatedDuration":1}]}
            """));
    }

    @Test
    void mrpRunNetsLedgerIntoProposalsAndEvents() throws Exception {
        // seed the ledger through the JPA store the HTTP slice leaves cold
        eventStore.save(new MrpEvent("s-1", "P-A", MrpEventType.SUPPLY,
            new BigDecimal("12"), Instant.parse("2026-01-01T00:00:00Z")));
        eventStore.save(new MrpEvent("d-1", "P-A", MrpEventType.DEMAND,
            new BigDecimal("15"), Instant.parse("2026-01-02T00:00:00Z")));
        eventStore.save(new MrpEvent("d-2", "P-B", MrpEventType.DEMAND,
            BigDecimal.TEN, Instant.parse("2026-01-02T00:00:00Z")));
        eventStore.save(new MrpEvent("s-2", "P-B", MrpEventType.SUPPLY,
            BigDecimal.TEN, Instant.parse("2026-01-03T00:00:00Z")));
        eventStore.save(new MrpEvent("d-3", "P-C", MrpEventType.DEMAND,
            new BigDecimal("4"), Instant.parse("2026-01-02T00:00:00Z")));
        eventStore.save(new MrpEvent("s-3", "P-C", MrpEventType.SUPPLY,
            new BigDecimal("10"), Instant.parse("2026-01-01T00:00:00Z")));

        JsonNode run = post("/api/v1/mrp/run", "{}");
        assertTrue(run.path("proposals").isArray());
        assertEquals(1, run.path("proposals").size(), "only the short products propose");
        JsonNode proposalA = run.path("proposals").get(0);
        assertEquals("P-A", proposalA.path("productId").asString());
        assertEquals(3, proposalA.path("quantity").asInt(), "15 demand - 12 supply");
        // P-B nets to zero (demand 10, supply 10) and P-C is covered
        // (demand 4, supply 10): neither proposes

        JsonNode proposals = get("/api/v1/mrp/proposals");
        assertEquals(1, proposals.size(), "proposals persist from the run");
        JsonNode events = get("/api/v1/mrp/events");
        assertEquals(6, events.size());
        assertTrue(outbox.findAll().stream()
            .anyMatch(e -> "manufacturing.mrp.proposed.v1".equals(e.getEventType())
                && e.getAggregateKey().equals("P-A")));
    }
}
