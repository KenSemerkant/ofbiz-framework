package identity.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/**
 * P1-F1-S1 AC1: OIDC discovery document is served with the issuer,
 * JWKS URI and the supported grants (auth-code+PKCE, client-credentials,
 * refresh). Password grant must never be advertised (AC4 security check).
 */
class DiscoveryDocumentTest extends IdentityIntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void discoveryDocumentIsServed() throws Exception {
        HttpResponse<String> response = http.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/.well-known/openid-configuration")).GET().build(),
            HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "discovery doc must be served: " + response.body());

        String body = response.body();
        assertTrue(body.contains("\"issuer\""), "issuer must be present: " + body);
        assertTrue(body.contains("\"jwks_uri\""), "jwks_uri must be present: " + body);
        assertTrue(body.contains("\"authorization_code\""), "authorization_code supported: " + body);
        assertTrue(body.contains("\"client_credentials\""), "client_credentials supported: " + body);
        assertTrue(body.contains("\"refresh_token\""), "refresh_token supported: " + body);
        assertTrue(!body.contains("\"password\""), "password grant must be absent (AC4): " + body);
    }
}
