package identity.adapters.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import identity.application.PartyProjectionStore;
import identity.application.UserLoginStore;
import identity.domain.PartyProjection;
import identity.domain.UserLogin;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * P2-F3-S1 AC: token roles come from the party projection; a revoked role
 * (projection updated by party.role.revoked) disappears from resolution.
 */
class RoleClaimResolverTest {

    private final Map<String, PartyProjection> projectionData = new HashMap<>();

    private final RoleClaimResolver resolver = new RoleClaimResolver(
        new UserLoginStore() {
            @Override
            public void save(UserLogin userLogin) {
            }

            @Override
            public Optional<UserLogin> find(String userLoginId) {
                return "ada".equals(userLoginId)
                    ? Optional.of(new UserLogin("ada", "P1", "hash", true))
                    : Optional.empty();
            }

            @Override
            public boolean exists(String userLoginId) {
                return false;
            }
        },
        new PartyProjectionStore() {
            @Override
            public boolean alreadyProcessed(String eventId) {
                return false;
            }

            @Override
            public void upsert(PartyProjection projection, String eventId) {
                projectionData.put(projection.partyId(), projection);
            }

            @Override
            public Optional<PartyProjection> find(String partyId) {
                return Optional.ofNullable(projectionData.get(partyId));
            }
        });

    @Test
    void rolesAreUnionOfUserAndProjection() {
        projectionData.put("P1",
            new PartyProjection("P1", "{\"roles\":[\"CUSTOMER\",\"SUPPLIER\"]}", Instant.now()));
        assertEquals(Set.of("USER", "CUSTOMER", "SUPPLIER"), resolver.resolve("ada"));
        assertEquals(Optional.of("P1"), resolver.partyIdOf("ada"));
    }

    @Test
    void revokedRoleDisappearsFromResolution() {
        projectionData.put("P1",
            new PartyProjection("P1", "{\"roles\":[\"CUSTOMER\",\"SUPPLIER\"]}", Instant.now()));
        // party.role.revoked applied -> projection rewritten without SUPPLIER
        projectionData.put("P1",
            new PartyProjection("P1", "{\"roles\":[\"CUSTOMER\"]}", Instant.now()));
        Set<String> roles = resolver.resolve("ada");
        assertTrue(roles.contains("CUSTOMER"));
        assertTrue(roles.stream().noneMatch("SUPPLIER"::equals));
    }

    @Test
    void machinePrincipalCarriesNoRoles() {
        assertEquals(Set.of(), resolver.resolve("gateway"));
    }
}
