package identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import identity.domain.UserLogin;
import org.junit.jupiter.api.Test;

/** Unit coverage for the identity application services (no containers). */
class IdentityApplicationServicesTest {

    private final Map<String, UserLogin> users = new HashMap<>();
    private final List<String> outbox = new ArrayList<>();
    private final UserLoginStore store = new UserLoginStore() {
        @Override
        public void save(UserLogin userLogin) {
            users.put(userLogin.userLoginId(), userLogin);
        }

        @Override
        public Optional<UserLogin> find(String userLoginId) {
            return Optional.ofNullable(users.get(userLoginId));
        }

        @Override
        public boolean exists(String userLoginId) {
            return users.containsKey(userLoginId);
        }
    };
    private final UserAdministrationService admin = new UserAdministrationService(
        store, new identity.domain.PasswordHasher() {
            @Override
            public String hash(String rawPassword) {
                return "{hash}" + rawPassword;
            }

            @Override
            public boolean matches(String rawPassword, String storedPassword) {
                return false;
            }
        });

    @Test
    void userLifecycle() {
        UserLogin created = admin.create("ada", "secret", "P1");
        assertEquals("{hash}secret", created.currentPassword());

        assertThrows(UserAdministrationService.DuplicateUserException.class,
            () -> admin.create("ada", "x", "P1"));
        assertThrows(IllegalArgumentException.class, () -> admin.create(" ", "x", "P1"));
        assertThrows(IllegalArgumentException.class, () -> admin.create("bob", " ", "P1"));

        var view = admin.view("ada");
        assertEquals("ada", view.userLoginId());
        assertEquals("P1", view.partyId());
        assertTrue(view.enabled());
        assertThrows(UserAdministrationService.UserNotFoundException.class,
            () -> admin.view("nobody"));
    }

    @Test
    void loginAuditGoesToOutbox() {
        new RecordLoginAuditService((type, payload) -> outbox.add(type + "|" + payload))
            .loginSucceeded("ada");
        assertEquals(1, outbox.size());
        assertTrue(outbox.get(0).startsWith("identity.login.succeeded|"));
        assertTrue(outbox.get(0).contains("\"userLoginId\":\"ada\""));
    }

    @Test
    void projectionSkipsAlreadyProcessedEventIds() {
        List<String> projected = new ArrayList<>();
        PartyProjectionStore projections = new PartyProjectionStore() {
            @Override
            public boolean alreadyProcessed(String eventId) {
                return projected.contains(eventId);
            }

            @Override
            public void upsert(identity.domain.PartyProjection projection, String eventId) {
                projected.add(eventId);
            }

            @Override
            public Optional<identity.domain.PartyProjection> find(String partyId) {
                return Optional.empty();
            }
        };
        var service = new PartyProjectionService(projections);
        service.apply("evt-1", "P1", "{}");
        service.apply("evt-1", "P1", "{}"); // replay — skipped
        assertEquals(List.of("evt-1"), projected);
    }
}
