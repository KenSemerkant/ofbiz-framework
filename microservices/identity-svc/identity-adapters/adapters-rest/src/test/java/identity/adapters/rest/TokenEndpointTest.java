package identity.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * P1-F1-S1 AC2 (issuance half): the registered test client can obtain a token
 * via client_credentials; a wrong secret is rejected with 401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TokenEndpointTest {

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> tokenRequest(String clientId, String clientSecret) throws Exception {
        String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());
        return http.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/oauth2/token"))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void clientCredentialsIssuesAccessToken() throws Exception {
        HttpResponse<String> response =
            tokenRequest("gateway-test-client", "test-secret");

        assertEquals(200, response.statusCode(), "token must be issued: " + response.body());
        assertTrue(response.body().contains("\"access_token\""), "access_token present: " + response.body());
        assertTrue(response.body().contains("\"Bearer\""), "token_type Bearer: " + response.body());
    }

    @Test
    void wrongClientSecretIsRejected() throws Exception {
        HttpResponse<String> response =
            tokenRequest("gateway-test-client", "wrong-secret");

        assertEquals(401, response.statusCode(), "wrong secret must yield 401: " + response.body());
    }
}
