package marketing.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import marketing.adapters.rest.persistence.OutboxRepository;
import marketing.application.AttributionStore;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * CG-6 marketing slice: campaign + tracking code + opportunity CRUD with
 * created/updated events in the outbox, guard violations as typed 409s,
 * missing aggregates as 404s, and the attribution report grouped by
 * tracking code with campaign resolution. Relay and ECA disabled — the
 * outbox rows ARE the event evidence (order-svc test convention);
 * on-cluster is the CG-6 lifecycle script.
 */
@SpringBootTest(properties = {"marketing.relay.enabled=false", "marketing.eca.enabled=false"},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class MarketingApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @LocalServerPort
    int port;

    @Autowired
    OutboxRepository outbox;

    @Autowired
    AttributionStore attributionStore;

    final HttpClient http = HttpClient.newHttpClient();
    final ObjectMapper json = new ObjectMapper();

    private JsonNode post(String path, String body) throws Exception {
        return json.readTree(send("POST", path, body));
    }

    private JsonNode put(String path, String body) throws Exception {
        return json.readTree(send("PUT", path, body));
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:%d%s".formatted(port, path))).GET().build();
        return json.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
    }

    private int status(String method, String path, String body) throws Exception {
        return http.send(request(method, path, body), HttpResponse.BodyHandlers.ofString())
            .statusCode();
    }

    private HttpRequest request(String method, String path, String body) {
        return HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:%d%s".formatted(port, path)))
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private String send(String method, String path, String body) throws Exception {
        return http.send(request(method, path, body), HttpResponse.BodyHandlers.ofString())
            .body();
    }

    @Test
    void campaignCrudLifecycleWithOutboxEvents() throws Exception {
        JsonNode created = post("/api/v1/campaigns", """
            {"marketingCampaignId":"MC-1","campaignName":"Spring Promo",
             "statusId":"PLANNED","fromDate":"2026-01-01","thruDate":"2026-12-31"}
            """);
        assertEquals("MC-1", created.path("marketingCampaignId").asText());
        assertTrue(outbox.findAll().stream()
            .anyMatch(e -> "marketing.campaign.created.v1".equals(e.getEventType())));

        JsonNode updated = put("/api/v1/campaigns/MC-1", """
            {"statusId":"ACTIVE"}
            """);
        assertEquals("ACTIVE", updated.path("statusId").asText());
        assertEquals("Spring Promo", updated.path("campaignName").asText());
        assertTrue(outbox.findAll().stream()
            .anyMatch(e -> "marketing.campaign.updated.v1".equals(e.getEventType())));

        assertTrue(get("/api/v1/campaigns").size() >= 1, "the campaign list is live");
        assertEquals(200, status("GET", "/api/v1/campaigns/MC-1", ""));
        assertEquals(404, status("GET", "/api/v1/campaigns/MC-404", ""));

        // guard violations are typed 409s
        assertEquals(409, status("POST", "/api/v1/campaigns", """
            {"marketingCampaignId":"MC-2","campaignName":"bad window",
             "statusId":"PLANNED","fromDate":"2026-12-31","thruDate":"2026-01-01"}
            """));
        assertEquals(409, status("PUT", "/api/v1/campaigns/MC-1", """
            {"statusId":"NOT_A_STATUS"}
            """));
    }

    @Test
    void trackingCodeLifecycleAndCampaignGuard() throws Exception {
        post("/api/v1/campaigns", """
            {"marketingCampaignId":"MC-T","campaignName":"Test",
             "statusId":"ACTIVE","fromDate":"2026-01-01","thruDate":"2026-12-31"}
            """);
        JsonNode created = post("/api/v1/tracking-codes", """
            {"trackingCodeId":"PROMO-42","marketingCampaignId":"MC-T",
             "description":"spring mail","fromDate":"2026-01-01","thruDate":"2026-12-31"}
            """);
        assertEquals("PROMO-42", created.path("trackingCodeId").asText());
        assertEquals("MC-T", created.path("marketingCampaignId").asText());
        assertTrue(outbox.findAll().stream()
            .anyMatch(e -> "marketing.trackingcode.created.v1".equals(e.getEventType())));

        JsonNode updated = put("/api/v1/tracking-codes/PROMO-42", """
            {"description":"renamed"}
            """);
        assertEquals("renamed", updated.path("description").asText());
        assertTrue(outbox.findAll().stream()
            .anyMatch(e -> "marketing.trackingcode.updated.v1".equals(e.getEventType())));

        assertTrue(get("/api/v1/tracking-codes").size() >= 1, "the tracking-code list is live");
        assertEquals(200, status("GET", "/api/v1/tracking-codes/PROMO-42", ""));
        assertEquals(404, status("GET", "/api/v1/tracking-codes/TC-404", ""));
        // unknown campaign on the FK-by-id link is a 404
        assertEquals(404, status("POST", "/api/v1/tracking-codes", """
            {"trackingCodeId":"TC-X","marketingCampaignId":"MC-404",
             "fromDate":"2026-01-01","thruDate":"2026-12-31"}
            """));
    }

    @Test
    void opportunityLifecycleAndProbabilityGuard() throws Exception {
        JsonNode created = post("/api/v1/opportunities", """
            {"salesOpportunityId":"SO-1","description":"big deal",
             "opportunityStageId":"QUALIFIED","closeProbability":65}
            """);
        assertEquals(65, created.path("closeProbability").asInt());

        JsonNode updated = put("/api/v1/opportunities/SO-1", """
            {"opportunityStageId":"WON","closeProbability":100}
            """);
        assertEquals("WON", updated.path("opportunityStageId").asText());
        assertTrue(outbox.findAll().stream()
            .anyMatch(e -> "marketing.opportunity.created.v1".equals(e.getEventType())
                || "marketing.opportunity.updated.v1".equals(e.getEventType())));

        assertTrue(get("/api/v1/opportunities").size() >= 1, "the opportunity list is live");
        assertEquals(200, status("GET", "/api/v1/opportunities/SO-1", ""));
        assertEquals(404, status("GET", "/api/v1/opportunities/SO-404", ""));
        assertEquals(409, status("PUT", "/api/v1/opportunities/SO-1", """
            {"closeProbability":101}
            """));
    }

    @Test
    void attributionReportGroupsByTrackingCodeAndResolvesCampaign() throws Exception {
        post("/api/v1/campaigns", """
            {"marketingCampaignId":"MC-A","campaignName":"Mail",
             "statusId":"ACTIVE","fromDate":"2026-01-01","thruDate":"2026-12-31"}
            """);
        post("/api/v1/tracking-codes", """
            {"trackingCodeId":"PROMO-42","marketingCampaignId":"MC-A",
             "fromDate":"2026-01-01","thruDate":"2026-12-31"}
            """);
        // the consumer lands rows via Kafka; here the store is the report input
        attributionStore.record(new AttributionStore.Attribution("ORDER-A1", "PROMO-42",
            new BigDecimal("22.00"), Instant.parse("2026-02-01T10:00:00Z")));
        attributionStore.record(new AttributionStore.Attribution("ORDER-A2", "PROMO-42",
            new BigDecimal("10.00"), Instant.parse("2026-02-02T10:00:00Z")));
        attributionStore.record(new AttributionStore.Attribution("ORDER-B1", "UNKNOWN-9",
            new BigDecimal("5.00"), Instant.parse("2026-02-03T10:00:00Z")));

        JsonNode report = get("/api/v1/attribution");
        assertEquals(2, report.size());
        // ordered by trackingCode: PROMO-42 first
        JsonNode promo = report.get(0);
        assertEquals("PROMO-42", promo.path("trackingCode").asText());
        assertEquals("MC-A", promo.path("campaignId").asText());
        assertEquals(2, promo.path("orderCount").asLong());
        assertEquals(32.00, promo.path("revenueTotal").asDouble(), 0.0001);
        JsonNode unknown = report.get(1);
        assertEquals("UNKNOWN-9", unknown.path("trackingCode").asText());
        assertTrue(unknown.path("campaignId").isNull(),
            "unknown codes report a null campaignId");

        JsonNode detail = get("/api/v1/attribution/PROMO-42");
        assertEquals("PROMO-42", detail.path("trackingCode").asText());
        assertEquals(2, detail.path("orderCount").asLong());
        assertEquals(2, detail.path("orders").size());
        assertEquals("ORDER-A1", detail.path("orders").get(0).path("orderId").asText());
        assertEquals(404, status("GET", "/api/v1/attribution/TC-404", ""));
    }
}
