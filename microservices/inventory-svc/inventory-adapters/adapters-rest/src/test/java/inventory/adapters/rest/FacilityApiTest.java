package inventory.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import inventory.adapters.rest.persistence.OutboxRepository;

/**
 * AC (P3-F2-S1): facility CRUD via REST with `inventory.facility.*` outbox
 * events in the same transaction (relay disabled — the outbox row IS the
 * event evidence). Boot 4 test style follows the repo convention: plain
 * HttpClient, no MockMvc/TestRestTemplate.
 */
@SpringBootTest(properties = "inventory.relay.enabled=false",
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class FacilityApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @LocalServerPort
    int port;

    @Autowired
    OutboxRepository outbox;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> postFacility(String body) throws Exception {
        return client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/facilities"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getFacility(String facilityId) throws Exception {
        return client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/facilities/" + facilityId))
            .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> deleteFacility(String facilityId) throws Exception {
        return client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/facilities/" + facilityId))
            .DELETE().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void facilityCrudRoundtripEmitsCreatedUpdatedDeleted() throws Exception {
        assertEquals(201, postFacility(
            "{\"facilityId\":\"WH1\",\"facilityName\":\"Main Warehouse\","
                + "\"facilityTypeId\":\"WAREHOUSE\"}").statusCode());
        assertEquals("inventory.facility.created.v1",
            outbox.findAll().stream()
                .filter(e -> "WH1".equals(e.getAggregateKey()))
                .min(java.util.Comparator.comparing(e -> e.getId()))
                .orElseThrow().getEventType());

        assertEquals(200, getFacility("WH1").statusCode());
        org.junit.jupiter.api.Assertions.assertTrue(
            getFacility("WH1").body().contains("Main Warehouse"));

        assertEquals(201, postFacility(
            "{\"facilityId\":\"WH1\",\"facilityName\":\"Annex\"}").statusCode());
        org.junit.jupiter.api.Assertions.assertTrue(
            getFacility("WH1").body().contains("Annex"));
        assertEquals("inventory.facility.updated.v1",
            outbox.findAll().stream()
                .filter(e -> "WH1".equals(e.getAggregateKey()))
                .max(java.util.Comparator.comparing(e -> e.getId()))
                .orElseThrow().getEventType());

        assertEquals(204, deleteFacility("WH1").statusCode());
        assertEquals(404, getFacility("WH1").statusCode());
        assertEquals("inventory.facility.deleted.v1",
            outbox.findAll().stream()
                .filter(e -> "WH1".equals(e.getAggregateKey()))
                .max(java.util.Comparator.comparing(e -> e.getId()))
                .orElseThrow().getEventType());
    }

    @Test
    void unknownFacilityIsNotFoundAndInvalidIsRejected() throws Exception {
        assertEquals(404, getFacility("nope").statusCode());
        assertEquals(404, deleteFacility("nope").statusCode());
        assertEquals(400, postFacility(
            "{\"facilityId\":\"WH1\",\"facilityName\":\" \"}").statusCode());
    }
}
