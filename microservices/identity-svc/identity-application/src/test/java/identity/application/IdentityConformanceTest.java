package identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import identity.domain.PasswordHasher;
import identity.domain.UserLogin;
import ofbiz.arch.OfbizArchitectureRules;
import org.junit.jupiter.api.Test;

/** ArchUnit layering evidence for identity-svc (ADR-007, P0-F4-S4 pattern). */
class IdentityConformanceTest {

    @Test
    void layeringHolds() {
        JavaClasses classes = new ClassFileImporter().importPackages("identity");
        OfbizArchitectureRules.layeredArchitecture().check(classes);
        OfbizArchitectureRules.noFrameworkDependenciesInDomain().check(classes);
    }

    @Test
    void blankUserLoginIdRejected() {
        assertThrows(IllegalArgumentException.class, () -> new UserLogin(" ", null, "x", true));
    }

    @Test
    void duplicateUserRejected() {
        UserLoginStore store = new UserLoginStore() {
            @Override public void save(UserLogin userLogin) {}
            @Override public java.util.Optional<UserLogin> find(String userLoginId) { return java.util.Optional.empty(); }
            @Override public boolean exists(String userLoginId) { return true; }
        };
        UserAdministrationService service = new UserAdministrationService(store, new PasswordHasher() {
            @Override public String hash(String rawPassword) { return "hashed"; }
            @Override public boolean matches(String rawPassword, String storedPassword) { return false; }
        });
        assertThrows(UserAdministrationService.DuplicateUserException.class,
            () -> service.create("dupe", "pw", null));
    }

    @Test
    void projectionSkipsAlreadyProcessedEvents() {
        boolean[] upserted = {false};
        PartyProjectionStore store = new PartyProjectionStore() {
            @Override public boolean alreadyProcessed(String eventId) { return true; }
            @Override public void upsert(identity.domain.PartyProjection projection, String eventId) { upserted[0] = true; }
            @Override public java.util.Optional<identity.domain.PartyProjection> find(String partyId) { return java.util.Optional.empty(); }
        };
        new PartyProjectionService(store).apply("evt-1", "party-1", "{}");
        assertEquals(false, upserted[0], "replayed event must be a no-op");
    }
}
