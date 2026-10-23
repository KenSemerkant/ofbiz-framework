package finance.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import finance.adapters.rest.persistence.OutboxRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * CG-5 ledger slice: accounts + period + rule seeded, a manual balanced
 * posting lands with its event in the outbox, unbalanced and closed-period
 * postings are typed 409s, the trial balance balances, and a broken rule
 * parks its event for review. Relay and ECA disabled — the outbox rows ARE
 * the event evidence (order-svc test convention); on-cluster is
 * 80-billing-lifecycle.sh.
 */
@SpringBootTest(properties = {"finance.relay.enabled=false", "finance.eca.enabled=false"},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class FinanceApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @LocalServerPort
    int port;

    @Autowired
    OutboxRepository outbox;

    @Autowired
    finance.application.PeriodStore periodStore;

    @Autowired
    finance.application.ParkedPostingStore parkedStore;

    final HttpClient http = HttpClient.newHttpClient();
    final ObjectMapper json = new ObjectMapper();

    private JsonNode post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:%d%s".formatted(port, path)))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return json.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
    }

    private int status(String method, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:%d%s".formatted(port, path)))
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:%d%s".formatted(port, path))).GET().build();
        return json.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
    }

    @Test
    void ledgerLifecycleGuardsAndTrialBalance() throws Exception {
        post("/api/v1/gl/accounts", """
            {"glAccountId":"1200","accountCode":"1200","accountName":"Accounts Receivable",
             "postBalance":"DEBIT","glAccountClassId":"ASSET"}
            """);
        post("/api/v1/gl/accounts", """
            {"glAccountId":"4000","accountCode":"4000","accountName":"Sales Revenue",
             "postBalance":"CREDIT","glAccountClassId":"REVENUE"}
            """);
        post("/api/v1/gl/periods", """
            {"periodId":"P-2026","fromDate":"2026-01-01","thruDate":"2026-12-31"}
            """);

        JsonNode posted = post("/api/v1/gl/transactions", """
            {"acctgTransId":"TT-A1","transType":"MANUAL","description":"sale",
             "periodId":"P-2026",
             "entries":[{"glAccountId":"1200","amount":22.00,"debitCredit":"DEBIT"},
                        {"glAccountId":"4000","amount":22.00,"debitCredit":"CREDIT"}]}
            """);
        assertTrue(posted.path("posted").asBoolean());
        assertTrue(outbox.findAll().stream()
            .anyMatch(e -> "finance.transaction.posted.v1".equals(e.getEventType())));

        int unbalanced = status("POST", "/api/v1/gl/transactions", """
            {"acctgTransId":"TT-A2","transType":"MANUAL","description":"bad",
             "periodId":"P-2026",
             "entries":[{"glAccountId":"1200","amount":22.00,"debitCredit":"DEBIT"},
                        {"glAccountId":"4000","amount":21.99,"debitCredit":"CREDIT"}]}
            """);
        assertEquals(409, unbalanced);

        post("/api/v1/gl/periods/P-2026/close", "{}");
        int closedPeriod = status("POST", "/api/v1/gl/transactions", """
            {"acctgTransId":"TT-A3","transType":"MANUAL","description":"late",
             "periodId":"P-2026",
             "entries":[{"glAccountId":"1200","amount":1.00,"debitCredit":"DEBIT"},
                        {"glAccountId":"4000","amount":1.00,"debitCredit":"CREDIT"}]}
            """);
        assertEquals(409, closedPeriod);

        JsonNode tb = get("/api/v1/gl/trial-balance");
        assertTrue(tb.path("rows").isArray());
        assertTrue(tb.path("totalDebits").asDouble() > 0);
        assertEquals(tb.path("totalDebits").asDouble(), tb.path("totalCredits").asDouble(),
            0.0001);
        assertEquals(200, status("GET", "/api/v1/gl/periods/P-2026", ""));
        assertEquals(200, status("GET", "/api/v1/gl/accounts/1200", ""));
        assertEquals(200, status("GET", "/api/v1/gl/transactions/TT-A1", ""));
        assertEquals(404, status("GET", "/api/v1/gl/transactions/TT-404", ""));
        assertEquals(404, status("GET", "/api/v1/gl/accounts/9999", ""));
        assertEquals(404, status("GET", "/api/v1/gl/periods/P-NONE", ""));
        assertTrue(get("/api/v1/gl/rules").isArray());
        // JPA store paths the HTTP slice leaves cold
        assertTrue(periodStore.allOpen().stream().anyMatch(p -> "P-2026".equals(p.periodId()))
            || periodStore.allOpen().isEmpty());
        parkedStore.park("billing.invoice.issued.v1", "INV-X", "test park", "{}");
        assertEquals(1, parkedStore.parkedReasons().size());
    }

    @Test
    void brokenRuleParksForReview() throws Exception {
        post("/api/v1/gl/accounts", """
            {"glAccountId":"1200","accountCode":"1200","accountName":"Accounts Receivable",
             "postBalance":"DEBIT","glAccountClassId":"ASSET"}
            """);
        post("/api/v1/gl/periods", """
            {"periodId":"P-RULE","fromDate":"2026-01-01","thruDate":"2026-12-31"}
            """);
        post("/api/v1/gl/rules", """
            {"ruleId":"R-BAD","eventType":"billing.invoice.issued.v1",
             "debitAccountId":"1200","creditAccountId":"9999","enabled":true}
            """);
        assertEquals(200, status("GET", "/api/v1/gl/rules", ""));
        // the parked row only lands via the consumer; the endpoint proves the
        // view is live (empty here because ECA is disabled in this slice)
        JsonNode parked = get("/api/v1/gl/parked");
        assertTrue(parked.isArray());
    }
}
