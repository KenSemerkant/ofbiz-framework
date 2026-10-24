package humanres.adapters.rest.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import humanres.application.PartyNotFoundException;
import humanres.application.PartyRoleClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * HTTP adapter for the onboarding saga's outbound port: granting the
 * EMPLOYEE role on party-svc. East-west call through the cluster
 * (ADR-010); base URL is config so tests can point it at a stub.
 */
public final class HttpIntegrations {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build();

    /**
     * Every saga-facing call is bounded (P4-F2-S1 fail-loud rule): a dead
     * neighbour surfaces as an exception after 5s, never a hang — the
     * onboarding saga must FAIL (retryable), not park forever.
     */
    private static HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5));
    }

    @Component
    public static class HttpPartyRoleClient implements PartyRoleClient {

        private final String baseUrl;

        public HttpPartyRoleClient(@Value("${humanres.party.base-url}") String baseUrl) {
            this.baseUrl = baseUrl;
        }

        /**
         * party-svc has no single-role endpoint, so this is a read-modify-
         * write over its upsert: GET the party, add EMPLOYEE to its roles,
         * POST it back with names and contact mechs carried through.
         */
        @Override
        public void addEmployeeRole(String partyId) {
            JsonNode party = fetch(partyId);
            ObjectNode upsert = JSON.createObjectNode();
            upsert.put("partyId", partyId);
            upsert.put("kind", party.path("kind").asText());
            if ("PERSON".equals(party.path("kind").asText())) {
                // displayName is "First Last"; the upsert needs the split back
                String display = party.path("displayName").asText();
                int split = display.indexOf(' ');
                if (split < 1 || split == display.length() - 1) {
                    throw new IllegalStateException(
                        "party %s has no two-part display name".formatted(partyId));
                }
                upsert.put("firstName", display.substring(0, split));
                upsert.put("lastName", display.substring(split + 1));
            } else {
                upsert.put("groupName", party.path("displayName").asText());
            }
            ArrayNode roles = JSON.createArrayNode();
            party.path("roles").forEach(role -> roles.add(role.asText()));
            // grant is idempotent: party roles are a set
            roles.add("EMPLOYEE");
            upsert.set("roles", roles);
            if (!party.path("contactMechs").isMissingNode()) {
                upsert.set("contactMechs", party.path("contactMechs"));
            }
            post("/api/v1/parties", upsert.toString(), partyId);
        }

        private JsonNode fetch(String partyId) {
            HttpResponse<String> response;
            try {
                response = CLIENT.send(
                    request(URI.create(baseUrl + "/api/v1/parties/" + partyId)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                throw new IllegalStateException("party-svc fetch failed: " + e.getMessage(), e);
            }
            if (response.statusCode() == 404) {
                throw new PartyNotFoundException(partyId);
            }
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                    "party-svc fetch failed " + response.statusCode());
            }
            return JSON.readTree(response.body());
        }

        private void post(String path, String body, String partyId) {
            HttpResponse<String> response;
            try {
                response = CLIENT.send(request(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                throw new IllegalStateException(
                    "party-svc role grant failed: " + e.getMessage(), e);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("party-svc role grant for " + partyId
                    + " failed " + response.statusCode());
            }
        }
    }

    private HttpIntegrations() {
    }
}
