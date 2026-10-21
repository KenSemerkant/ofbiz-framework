package party.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
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
import party.adapters.rest.persistence.OutboxRepository;

/**
 * AC (P2-F1-S1): upsert/fetch via REST and the outbox row lands in the same
 * transaction (relay disabled — the outbox row IS the event evidence).
 * Boot 4 test style follows the repo convention: plain HttpClient, no
 * MockMvc/TestRestTemplate.
 */
@SpringBootTest(properties = {"party.relay.enabled=false", "party.grpc.port=0"},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PartyApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @LocalServerPort
    int port;

    @Autowired
    OutboxRepository outbox;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> post(String body) throws Exception {
        return client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/parties"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String partyId) throws Exception {
        return client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/parties/" + partyId))
            .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void upsertThenFetchRoundtripEmitsCreatedThenUpdated() throws Exception {
        HttpResponse<String> created = post("""
            {"partyId":"P1","kind":"PERSON","firstName":"Ada","lastName":"Lovelace",
             "roles":["CUSTOMER"]}""");
        assertEquals(201, created.statusCode());
        assertTrue(created.body().contains("Ada Lovelace"));
        assertEquals("party.party.created.v1", outbox.findAll().getFirst().getEventType());

        assertEquals(201, post(
            "{\"partyId\":\"P1\",\"kind\":\"PERSON\",\"firstName\":\"Ada\",\"lastName\":\"King\"}")
            .statusCode());
        // second upsert drops the CUSTOMER role: updated + role.revoked
        assertEquals(List.of("party.party.updated.v1", "party.role.revoked.v1"),
            outbox.findAll().stream().skip(2).map(e -> e.getEventType()).toList());

        HttpResponse<String> fetched = get("P1");
        assertEquals(200, fetched.statusCode());
        assertTrue(fetched.body().contains("Ada King"));
    }

    @Test
    void unknownPartyIsNotFound() throws Exception {
        assertEquals(404, get("unknown").statusCode());
    }
}
