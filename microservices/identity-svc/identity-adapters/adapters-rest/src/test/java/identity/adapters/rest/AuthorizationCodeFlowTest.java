package identity.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * P1-F1-S1 AC3: full authorization-code + PKCE flow against the running
 * server — login, consent-free code issuance, token exchange, and a
 * successful refresh_token grant.
 */
class AuthorizationCodeFlowTest extends IdentityIntegrationTest {

    private static final String REDIRECT_URI = "http://127.0.0.1:8080/login/oauth2/code/gateway";
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

    private final CookieManager cookies = new CookieManager();
    private final HttpClient http = HttpClient.newBuilder()
        .cookieHandler(cookies)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    private String base() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> get(String uri) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(uri)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String uri, String contentType, String body, String basicAuth) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(uri))
            .header("Content-Type", contentType)
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (basicAuth != null) {
            builder.header("Authorization", "Basic " + basicAuth);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String form(Map<String, String> fields) {
        var sb = new StringBuilder();
        fields.forEach((k, v) -> {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(java.net.URLEncoder.encode(k, StandardCharsets.UTF_8))
              .append('=')
              .append(java.net.URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    private static Map<String, String> parseQuery(String query) {
        var out = new HashMap<String, String>();
        for (var pair : query.split("&")) {
            var idx = pair.indexOf('=');
            if (idx > 0) {
                out.put(URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    /** Walks the login -> authorize chain and returns the ?code= query params. */
    private Map<String, String> obtainAuthorizationCode(String codeChallenge) throws Exception {
        String authorizeUrl = base() + "/oauth2/authorize?" + form(Map.of(
            "response_type", "code",
            "client_id", "gateway-test-client",
            "scope", "openid",
            "state", "s1-test-state",
            "redirect_uri", REDIRECT_URI,
            "code_challenge", codeChallenge,
            "code_challenge_method", "S256"));

        // 1. Authorize request -> redirected to login (browser-like Accept:
        //    the autoconfigured entry point only redirects text/html requests).
        HttpResponse<String> first = http.send(
            HttpRequest.newBuilder(URI.create(authorizeUrl))
                .header("Accept", "text/html")
                .GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(302, first.statusCode(), "authorize should redirect to login");

        // 2. Fetch the login page and extract the CSRF token.
        HttpResponse<String> loginPage = get(first.headers().firstValue("Location").orElseThrow());
        assertEquals(200, loginPage.statusCode(), "login page must render");
        var csrfMatcher = CSRF.matcher(loginPage.body());
        assertTrue(csrfMatcher.find(), "login page must carry a CSRF token");

        // 3. Post credentials; server redirects back to the authorize endpoint,
        //    which then redirects to the redirect_uri with ?code=...
        HttpResponse<String> login = post(base() + "/login", "application/x-www-form-urlencoded",
            form(Map.of("username", "test-user", "password", "test-password", "_csrf", csrfMatcher.group(1))), null);
        assertEquals(302, login.statusCode(), "login should redirect");
        String afterLogin = login.headers().firstValue("Location").orElseThrow();

        String next = afterLogin.startsWith("/") ? base() + afterLogin : afterLogin;
        HttpResponse<String> authorized = get(next);
        String location = authorized.headers().firstValue("Location").orElse("");
        assertTrue(location.startsWith(REDIRECT_URI), "must land on redirect_uri, got: " + location);

        return parseQuery(location.substring(location.indexOf('?') + 1));
    }

    private HttpResponse<String> tokenRequest(Map<String, String> fields) throws Exception {
        String basic = Base64.getEncoder()
            .encodeToString("gateway-test-client:test-secret".getBytes());
        return post(base() + "/oauth2/token", "application/x-www-form-urlencoded", form(fields), basic);
    }

    @Test
    void authorizationCodePkceAndRefreshWork() throws Exception {
        // PKCE verifier + S256 challenge.
        String verifier = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("verifier-material-32-bytes-aaaaaaaaa".getBytes());
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes());
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);

        Map<String, String> auth = obtainAuthorizationCode(challenge);
        assertEquals("s1-test-state", auth.get("state"), "state must round-trip");
        String code = auth.get("code");
        assertNotNull(code, "authorization code must be issued");

        // Exchange the code for tokens.
        HttpResponse<String> tokens = tokenRequest(Map.of(
            "grant_type", "authorization_code",
            "code", code,
            "redirect_uri", REDIRECT_URI,
            "code_verifier", verifier));
        assertEquals(200, tokens.statusCode(), "token exchange must succeed: " + tokens.body());
        assertTrue(tokens.body().contains("\"access_token\""), "access_token present");
        assertTrue(tokens.body().contains("\"refresh_token\""), "refresh_token present");

        String refreshToken = extractJsonString(tokens.body(), "refresh_token");

        // Refresh grant: new access token from the refresh token.
        HttpResponse<String> refreshed = tokenRequest(Map.of(
            "grant_type", "refresh_token",
            "refresh_token", refreshToken));
        assertEquals(200, refreshed.statusCode(), "refresh must succeed: " + refreshed.body());
        assertNotEquals(extractJsonString(tokens.body(), "access_token"),
            extractJsonString(refreshed.body(), "access_token"),
            "refreshed access_token must differ from the original");
    }

    /** Minimal JSON string-field extractor (Jackson not on the test classpath). */
    private static String extractJsonString(String json, String field) {
        var m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        assertTrue(m.find(), field + " must be present in: " + json);
        return m.group(1);
    }
}
