package humanres.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import humanres.adapters.rest.persistence.OutboxRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * P6-T3 slice: positions/employments CRUD with typed 409/404, and the
 * onboarding saga end-to-end against a stub party-svc (com.sun.net.httpserver
 * — the real HttpPartyRoleClient runs, base URL pointed at the stub).
 * Relay, projection and resume disabled — the outbox rows ARE the event
 * evidence (finance-svc test convention).
 */
@SpringBootTest(properties = {"humanres.relay.enabled=false",
        "humanres.projection.enabled=false", "humanres.saga.enabled=false"},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class HumanresApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    /** party-svc stand-in: knows P-100/P-FAIL, not GHOST; can fail POSTs on demand. */
    static final PartyStub PARTY_STUB = new PartyStub();

    @DynamicPropertySource
    static void partyBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("humanres.party.base-url",
            () -> "http://localhost:" + PARTY_STUB.port);
    }

    static final class PartyStub {

        final int port;
        final AtomicBoolean failRolePosts = new AtomicBoolean(false);
        final AtomicReference<String> lastUpsertBody = new AtomicReference<>("");

        PartyStub() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
                server.createContext("/", this::handle);
                server.start();
                this.port = server.getAddress().getPort();
            } catch (IOException e) {
                throw new IllegalStateException("party stub failed to start", e);
            }
        }

        private void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if ("GET".equals(method) && path.startsWith("/api/v1/parties/")) {
                String partyId = path.substring(path.lastIndexOf('/') + 1);
                if ("GHOST".equals(partyId)) {
                    respond(exchange, 404, "{\"error\":\"no party\"}");
                } else {
                    respond(exchange, 200, """
                        {"partyId":"%s","kind":"PERSON","displayName":"Alex Smith",\
                        "roles":["CUSTOMER"],"contactMechs":[]}
                        """.formatted(partyId));
                }
            } else if ("POST".equals(method) && "/api/v1/parties".equals(path)) {
                lastUpsertBody.set(new String(exchange.getRequestBody().readAllBytes()));
                if (failRolePosts.get()) {
                    respond(exchange, 500, "{\"error\":\"party-svc down\"}");
                } else {
                    respond(exchange, 201, "{}");
                }
            } else {
                respond(exchange, 404, "{}");
            }
            exchange.close();
        }

        private void respond(HttpExchange exchange, int status, String body)
                throws IOException {
            byte[] bytes = body.getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    OutboxRepository outbox;

    @Autowired
    JdbcTemplate db;

    @Autowired
    humanres.application.PartyProjectionStore projections;

    final HttpClient http = HttpClient.newHttpClient();
    final ObjectMapper json = new ObjectMapper();

    private JsonNode post(String path, String body) throws Exception {
        return send("POST", path, body);
    }

    private JsonNode send(String method, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:%d%s".formatted(port, path)))
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        return json.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
    }

    private int status(String method, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:%d%s".formatted(port, path)))
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private void project(String partyId) {
        projections.upsert(partyId, "{\"firstName\":\"Alex\",\"lastName\":\"Smith\"}");
    }

    @Test
    void positionAndEmploymentCrudGuards() throws Exception {
        assertEquals(201, status("POST", "/api/v1/positions", """
            {"emplPositionId":"POS-API","emplPositionTypeId":"ENGINEER",
             "reportingToEmplPositionId":null,"statusId":"ACTIVE"}
            """));
        assertEquals(200, status("GET", "/api/v1/positions/POS-API", ""));
        JsonNode updated = json.readTree(http.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:%d/api/v1/positions/POS-API".formatted(port)))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("""
                {"emplPositionTypeId":"SENIOR_ENGINEER",
                 "reportingToEmplPositionId":null,"statusId":"ACTIVE"}
                """)).build(), HttpResponse.BodyHandlers.ofString()).body());
        assertEquals("SENIOR_ENGINEER", updated.path("emplPositionTypeId").asText());
        assertEquals(409, status("POST", "/api/v1/positions", """
            {"emplPositionId":"POS-API","emplPositionTypeId":"ENGINEER",
             "reportingToEmplPositionId":null,"statusId":"ACTIVE"}
            """));
        assertEquals(404, status("GET", "/api/v1/positions/POS-404", ""));
        assertEquals(404, status("POST", "/api/v1/positions", """
            {"emplPositionId":"POS-ORPHAN","emplPositionTypeId":"ENGINEER",
             "reportingToEmplPositionId":"POS-404","statusId":"ACTIVE"}
            """));

        assertEquals(201, status("POST", "/api/v1/employments", """
            {"employmentId":"EMP-API","partyId":"P-100","emplPositionId":"POS-API",
             "fromDate":"2026-10-01","thruDate":null}
            """));
        assertEquals(200, status("GET", "/api/v1/employments/EMP-API", ""));
        assertEquals(409, status("POST", "/api/v1/employments", """
            {"employmentId":"EMP-API","partyId":"P-100","emplPositionId":"POS-API",
             "fromDate":"2026-10-01","thruDate":null}
            """));
        assertEquals(404, status("POST", "/api/v1/employments", """
            {"employmentId":"EMP-X","partyId":"P-100","emplPositionId":"POS-404",
             "fromDate":"2026-10-01","thruDate":null}
            """));
        assertEquals(400, status("POST", "/api/v1/employments", """
            {"employmentId":"EMP-BAD","partyId":"P-100","emplPositionId":"POS-API",
             "fromDate":"2026-10-01","thruDate":"2026-09-30"}
            """));
        assertEquals(200, status("PUT", "/api/v1/employments/EMP-API",
            "{\"thruDate\":\"2026-12-31\"}"));
        assertEquals(404, status("GET", "/api/v1/employments/EMP-404", ""));

        List<String> eventTypes = outbox.findAll().stream()
            .map(e -> e.getEventType()).toList();
        assertTrue(eventTypes.contains("humanres.position.created.v1"));
        assertTrue(eventTypes.contains("humanres.position.updated.v1"));
        assertTrue(eventTypes.contains("humanres.employment.created.v1"));
    }

    @Test
    void onboardingSagaRunsBothStepsAgainstPartySvc() throws Exception {
        project("P-100");
        assertEquals(201, status("POST", "/api/v1/positions", """
            {"emplPositionId":"POS-ONB","emplPositionTypeId":"ENGINEER",
             "reportingToEmplPositionId":null,"statusId":"ACTIVE"}
            """));
        JsonNode saga = post("/api/v1/onboarding", """
            {"partyId":"P-100","emplPositionId":"POS-ONB"}
            """);
        String onboardingId = saga.path("onboardingId").asText();
        assertTrue(onboardingId.startsWith("ONB-"));
        assertEquals("COMPLETED", saga.path("status").asText());
        assertTrue(saga.path("steps").isArray());
        assertEquals(2, saga.path("steps").size());

        // the role grant really went to party-svc with EMPLOYEE added
        JsonNode upsert = json.readTree(PARTY_STUB.lastUpsertBody.get());
        assertEquals("P-100", upsert.path("partyId").asText());
        assertTrue(upsert.path("roles").toString().contains("EMPLOYEE"));
        assertTrue(upsert.path("roles").toString().contains("CUSTOMER"),
            "existing roles must survive the grant");
        assertEquals("Alex", upsert.path("firstName").asText());

        String employmentId = "EMP-" + onboardingId;
        assertEquals(200, status("GET", "/api/v1/employments/" + employmentId, ""));
        JsonNode fetched = send("GET", "/api/v1/onboarding/" + onboardingId, "");
        assertEquals("COMPLETED", fetched.path("status").asText());
        List<String> eventTypes = outbox.findAll().stream()
            .map(e -> e.getEventType()).toList();
        assertTrue(eventTypes.contains("humanres.employment.created.v1"));
        assertTrue(eventTypes.contains("humanres.onboarding.completed.v1"));

        assertEquals(404, status("GET", "/api/v1/onboarding/ONB-404", ""));
    }

    @Test
    void unknownPartyIs404AndFailedGrantRetriesToCompletion() throws Exception {
        project("P-100");
        project("P-FAIL");
        assertEquals(201, status("POST", "/api/v1/positions", """
            {"emplPositionId":"POS-RETRY","emplPositionTypeId":"ENGINEER",
             "reportingToEmplPositionId":null,"statusId":"ACTIVE"}
            """));
        assertEquals(404, status("POST", "/api/v1/onboarding", """
            {"partyId":"GHOST","emplPositionId":"POS-RETRY"}
            """));
        assertEquals(404, status("POST", "/api/v1/onboarding", """
            {"partyId":"P-100","emplPositionId":"POS-404"}
            """));
        assertEquals(0, db.queryForObject(
            "select count(*) from onboarding_saga where party_id = 'GHOST'", Integer.class));

        PARTY_STUB.failRolePosts.set(true);
        JsonNode failed = post("/api/v1/onboarding", """
            {"partyId":"P-FAIL","emplPositionId":"POS-RETRY"}
            """);
        String onboardingId = failed.path("onboardingId").asText();
        assertEquals("FAILED", failed.path("status").asText());
        assertNotNull(failed.path("failedReason").asText());

        PARTY_STUB.failRolePosts.set(false);
        JsonNode retried = post("/api/v1/onboarding/" + onboardingId + "/retry", "{}");
        assertEquals("COMPLETED", retried.path("status").asText());
        assertEquals(200, status("GET",
            "/api/v1/employments/EMP-" + onboardingId, ""));
    }
}
