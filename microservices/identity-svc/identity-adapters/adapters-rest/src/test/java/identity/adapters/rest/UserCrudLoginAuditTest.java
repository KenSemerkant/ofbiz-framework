package identity.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import identity.adapters.rest.persistence.OutboxRepository;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * P1-F1-S2 AC1+AC3 end-to-end: the admin API is restricted to ROLE_ADMIN
 * (401 anonymous, 403 plain user, 201 admin), a user created through it can
 * log in, and the login lands an audit event in the outbox (relay off in
 * tests — the Kafka hop is covered by PartyProjectionConsumerTest).
 */
class UserCrudLoginAuditTest extends IdentityIntegrationTest {

    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

    @Autowired
    private OutboxRepository outbox;

    private final CookieManager cookies = new CookieManager();
    private final HttpClient http = HttpClient.newBuilder()
        .cookieHandler(cookies)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    private String base() {
        return "http://localhost:" + port;
    }

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString(
            (user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> postUser(HttpClient client, String authorization, String body)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base() + "/api/v1/users"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (authorization != null && !authorization.isBlank()) {
            request.header("Authorization", authorization);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void createUserVerifyAndAuditRoundtrip() throws Exception {
        String newUser = "{\"userLoginId\":\"admin-created\",\"password\":\"s3cret!pw\",\"partyId\":\"party-9\"}";

        // Anonymous and non-admin callers are rejected before any write.
        HttpClient anonymous = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> anonymousResponse = postUser(anonymous, "", newUser);
        assertEquals(401, anonymousResponse.statusCode(),
            "anonymous must not create users; headers=" + anonymousResponse.headers().map());
        assertEquals(403,
            postUser(anonymous, basic("test-viewer", "viewer-password"), newUser).statusCode(),
            "plain users must not create users");

        // Create via admin API (HTTP Basic, stateless).
        String admin = basic("test-admin", "admin-password");
        HttpResponse<String> created = postUser(http, admin, newUser);
        assertEquals(201, created.statusCode(), "admin must create the user: " + created.body());
        assertTrue(!created.body().contains("s3cret!pw"), "response must not echo the password");
        assertTrue(created.body().contains("admin-created"));

        // Fetch back — roundtrip proof.
        HttpResponse<String> fetched = http.send(
            HttpRequest.newBuilder(URI.create(base() + "/api/v1/users/admin-created"))
                .header("Authorization", admin).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, fetched.statusCode());
        assertTrue(fetched.body().contains("party-9"));

        // Duplicate is rejected.
        assertEquals(409, postUser(http, admin, newUser).statusCode());

        // Log in with the new credentials through the real form.
        long before = outbox.count();
        HttpResponse<String> loginPage = http.send(
            HttpRequest.newBuilder(URI.create(base() + "/login")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        var csrf = CSRF.matcher(loginPage.body());
        assertTrue(csrf.find(), "login page carries CSRF token");
        HttpResponse<String> login = http.send(
            HttpRequest.newBuilder(URI.create(base() + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(
                    "username=admin-created&password=s3cret!pw&_csrf=" + csrf.group(1)))
                .build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(302, login.statusCode(), "new credentials must authenticate");

        // Audit event landed in the outbox.
        assertTrue(outbox.count() > before, "login must append an outbox row");
        assertTrue(outbox.findUnpublished().stream()
            .anyMatch(e -> "identity.login.succeeded".equals(e.getEventType())
                && e.getPayload().contains("admin-created")));
    }
}
