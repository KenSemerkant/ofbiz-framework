package workeffort.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import workeffort.adapters.rest.persistence.OutboxRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * CG-6 slice (P6-T2): effort CRUD + tree + status guards, timesheet
 * submit-once, and the iCal feed an external calendar must accept —
 * text/calendar, CRLF lines, UID/<id>@ofbiz, VALUE=DATE, escaped SUMMARY.
 * Relay disabled — the outbox rows ARE the event evidence (finance-svc
 * test convention); on-cluster is the platform gate drill.
 */
@SpringBootTest(properties = "workeffort.relay.enabled=false",
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class WorkeffortApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @LocalServerPort
    int port;

    @Autowired
    OutboxRepository outbox;

    @Autowired
    workeffort.application.WorkEffortStore effortStore;

    @Autowired
    workeffort.application.TimesheetStore timesheetStore;

    final HttpClient http = HttpClient.newHttpClient();
    final ObjectMapper json = new ObjectMapper();

    private JsonNode post(String path, String body) throws Exception {
        return json.readTree(send("POST", path, body).body());
    }

    private int status(String method, String path, String body) throws Exception {
        return send(method, path, body).statusCode();
    }

    private HttpResponse<String> send(String method, String path, String body)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:%d%s".formatted(port, path)))
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode get(String path) throws Exception {
        return json.readTree(send("GET", path, "").body());
    }

    @Test
    void effortLifecycleTreeAndStatusGuards() throws Exception {
        post("/api/v1/workefforts", """
            {"workEffortId":"WE-PROJ","workEffortTypeId":"PROJECT",
             "workEffortName":"Cutover, phase 6","parentWorkEffortId":null,
             "estimatedStartDate":"2026-01-05","estimatedCompletionDate":"2026-01-31"}
            """);
        post("/api/v1/workefforts", """
            {"workEffortId":"WE-T1","workEffortTypeId":"TASK",
             "workEffortName":"wire ical feed","parentWorkEffortId":"WE-PROJ",
             "estimatedStartDate":"2026-01-06","estimatedCompletionDate":"2026-01-10"}
            """);
        post("/api/v1/workefforts", """
            {"workEffortId":"WE-BARE","workEffortTypeId":"EVENT",
             "workEffortName":"standup","parentWorkEffortId":null,
             "estimatedStartDate":null,"estimatedCompletionDate":null}
            """);

        assertTrue(outbox.findAll().stream()
            .anyMatch(e -> "workeffort.effort.created.v1".equals(e.getEventType())));

        // tree query: one level only
        assertEquals(1, get("/api/v1/workefforts?parent=WE-PROJ").size());
        assertEquals("WE-T1", get("/api/v1/workefforts?parent=WE-PROJ").get(0)
            .path("workEffortId").asText());
        // sibling methods share the container: assert presence, not totals
        java.util.List<String> ids = new java.util.ArrayList<>();
        get("/api/v1/workefforts").forEach(e -> ids.add(e.path("workEffortId").asText()));
        assertTrue(ids.containsAll(java.util.List.of("WE-PROJ", "WE-T1", "WE-BARE")));

        // unknown parent is a 404, not an orphan row
        assertEquals(404, status("POST", "/api/v1/workefforts", """
            {"workEffortId":"WE-ORPHAN","workEffortTypeId":"TASK",
             "workEffortName":"orphan","parentWorkEffortId":"WE-GONE",
             "estimatedStartDate":null,"estimatedCompletionDate":null}
            """));

        // forward-only machine: skip and resubmit are typed 409s
        assertEquals(409, status("POST", "/api/v1/workefforts/WE-T1/status",
            "{\"statusId\":\"COMPLETED\"}"));
        post("/api/v1/workefforts/WE-T1/status", "{\"statusId\":\"IN_PROGRESS\"}");
        post("/api/v1/workefforts/WE-T1/status", "{\"statusId\":\"COMPLETED\"}");
        assertEquals("COMPLETED",
            get("/api/v1/workefforts/WE-T1").path("statusId").asText());
        assertEquals(409, status("POST", "/api/v1/workefforts/WE-T1/status",
            "{\"statusId\":\"IN_PROGRESS\"}"));
        // cancel still works from a live state
        post("/api/v1/workefforts/WE-BARE/status", "{\"statusId\":\"CANCELLED\"}");
        assertEquals(409, status("POST", "/api/v1/workefforts/WE-BARE/status",
            "{\"statusId\":\"IN_PROGRESS\"}"));

        // update emits its event and 404s on unknown ids
        assertEquals(200, status("PUT", "/api/v1/workefforts/WE-T1", """
            {"workEffortName":"wire ical feed (done)","parentWorkEffortId":"WE-PROJ",
             "estimatedStartDate":"2026-01-06","estimatedCompletionDate":"2026-01-10"}
            """));
        assertEquals(404, status("PUT", "/api/v1/workefforts/WE-NONE", """
            {"workEffortName":"x","parentWorkEffortId":null,
             "estimatedStartDate":null,"estimatedCompletionDate":null}
            """));
        assertTrue(outbox.findAll().stream()
            .anyMatch(e -> "workeffort.effort.updated.v1".equals(e.getEventType())));
        assertEquals(404, status("GET", "/api/v1/workefforts/WE-404", ""));
        assertEquals(204, status("DELETE", "/api/v1/workefforts/WE-BARE", ""));
        assertEquals(404, status("GET", "/api/v1/workefforts/WE-BARE", ""));
        // JPA store paths the HTTP slice leaves cold
        assertTrue(effortStore.findAllCalendarReady().stream()
            .allMatch(e -> e.isCalendarReady()));
    }

    @Test
    void calendarFeedIsValidIcalAnExternalCalendarAccepts() throws Exception {
        post("/api/v1/workefforts", """
            {"workEffortId":"WE-CAL1","workEffortTypeId":"PROJECT",
             "workEffortName":"Road, Curve; Bench","parentWorkEffortId":null,
             "estimatedStartDate":"2026-02-02","estimatedCompletionDate":"2026-02-06"}
            """);
        post("/api/v1/workefforts", """
            {"workEffortId":"WE-CAL2","workEffortTypeId":"TASK",
             "workEffortName":"dateless task","parentWorkEffortId":null,
             "estimatedStartDate":null,"estimatedCompletionDate":null}
            """);

        HttpResponse<String> feed = send("GET", "/api/v1/workefforts/calendar.ics", "");
        assertEquals(200, feed.statusCode());
        assertTrue(feed.headers().firstValue("Content-Type").orElseThrow()
            .startsWith("text/calendar"));
        String body = feed.body();
        assertTrue(body.startsWith("BEGIN:VCALENDAR\r\n"));
        assertTrue(body.endsWith("END:VCALENDAR\r\n"));
        assertTrue(body.contains("UID:WE-CAL1@ofbiz\r\n"));
        assertTrue(body.contains("DTSTART;VALUE=DATE:20260202\r\n"));
        // DTEND exclusive: the inclusive 6th ships as the 7th
        assertTrue(body.contains("DTEND;VALUE=DATE:20260207\r\n"));
        assertTrue(body.contains("DTSTAMP:20"));
        // RFC 5545 escaping in SUMMARY
        assertTrue(body.contains("SUMMARY:Road\\, Curve\\; Bench\r\n"));
        // efforts without both dates never reach the feed
        assertFalse(body.contains("WE-CAL2"));

        HttpResponse<String> single = send("GET", "/api/v1/workefforts/WE-CAL1/calendar.ics",
            "");
        assertEquals(200, single.statusCode());
        assertTrue(single.body().contains("UID:WE-CAL1@ofbiz"));
        assertTrue(single.headers().firstValue("Content-Type").orElseThrow()
            .startsWith("text/calendar"));
        assertEquals(404, status("GET", "/api/v1/workefforts/WE-NONE/calendar.ics", ""));
        assertEquals(409, status("GET", "/api/v1/workefforts/WE-CAL2/calendar.ics", ""),
            "dateless effort has no VEVENT to render");
    }

    @Test
    void timesheetSubmitIsOnceAndEntriesReferenceRealEfforts() throws Exception {
        post("/api/v1/workefforts", """
            {"workEffortId":"WE-TSW","workEffortTypeId":"TASK",
             "workEffortName":"timesheet work","parentWorkEffortId":null,
             "estimatedStartDate":"2026-01-05","estimatedCompletionDate":"2026-01-11"}
            """);
        post("/api/v1/timesheets", """
            {"timesheetId":"TS-1","partyId":"P-100","fromDate":"2026-01-05",
             "thruDate":"2026-01-11",
             "entries":[{"workEffortId":"WE-TSW","hours":7.5,"rateTypeId":"STANDARD"}]}
            """);
        assertEquals("DRAFT", get("/api/v1/timesheets/TS-1").path("statusId").asText());
        assertEquals(1, get("/api/v1/timesheets/TS-1").path("entries").size());

        // entries must reference real efforts
        assertEquals(404, status("POST", "/api/v1/timesheets", """
            {"timesheetId":"TS-GHOST","partyId":"P-100","fromDate":"2026-01-05",
             "thruDate":"2026-01-11",
             "entries":[{"workEffortId":"WE-GHOST","hours":1.0,"rateTypeId":"STANDARD"}]}
            """));

        post("/api/v1/timesheets/TS-1/submit", "{}");
        assertEquals("SUBMITTED", get("/api/v1/timesheets/TS-1").path("statusId").asText());
        assertTrue(outbox.findAll().stream().anyMatch(
            e -> "workeffort.timesheet.submitted.v1".equals(e.getEventType())));

        // resubmission and post-submission edits are typed 409s
        assertEquals(409, status("POST", "/api/v1/timesheets/TS-1/submit", "{}"));
        assertEquals(409, status("PUT", "/api/v1/timesheets/TS-1", """
            {"partyId":"P-100","fromDate":"2026-01-05","thruDate":"2026-01-11",
             "entries":[]}
            """));
        assertEquals(404, status("POST", "/api/v1/timesheets/TS-NONE/submit", "{}"));
        assertEquals(1, get("/api/v1/timesheets").size());
        assertEquals(404, status("GET", "/api/v1/timesheets/TS-404", ""));
        assertEquals(204, status("DELETE", "/api/v1/timesheets/TS-1", ""));
        // JPA store path the HTTP slice leaves cold
        assertTrue(timesheetStore.all().isEmpty());
    }
}
