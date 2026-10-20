package identity.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IdentityDomainTest {

    @Test
    void userLoginDefaultsToUserRole() {
        UserLogin user = new UserLogin("alice", "party-1", "hash", true);
        assertEquals(Set.of(UserLogin.ROLE_USER), user.roles());
        assertEquals("alice", user.userLoginId());
        assertEquals("party-1", user.partyId());
        assertEquals("hash", user.currentPassword());
        assertEquals(true, user.enabled());
    }

    @Test
    void userLoginKeepsExplicitRolesAndCopiesThem() {
        Set<String> roles = new java.util.HashSet<>(Set.of("ADMIN", "USER"));
        UserLogin user = new UserLogin("alice", null, "hash", true, roles);
        roles.add("OTHER");
        assertEquals(Set.of("ADMIN", "USER"), user.roles());
    }

    @Test
    void userLoginRejectsBlankIdAndNullRoles() {
        assertThrows(IllegalArgumentException.class,
            () -> new UserLogin(" ", null, "hash", true));
        assertEquals(Set.of("USER"), new UserLogin("alice", null, "hash", true, null).roles());
    }

    @Test
    void userLoginValueSemantics() {
        UserLogin one = new UserLogin("alice", "p", "h", true);
        UserLogin same = new UserLogin("alice", "p", "h", true);
        assertEquals(one, same);
        assertEquals(one.hashCode(), same.hashCode());
        assertNotEquals(one, new UserLogin("bob", "p", "h", true));
        assertEquals(one.toString(), same.toString());
    }

    @Test
    void partyProjectionValidatesAndCarriesTime() {
        Instant now = Instant.parse("2026-10-20T00:00:00Z");
        PartyProjection projection = new PartyProjection("party-1", "{}", now);
        assertEquals("party-1", projection.partyId());
        assertEquals("{}", projection.payload());
        assertEquals(now, projection.updatedAt());
        assertEquals(projection, new PartyProjection("party-1", "{}", now));
        assertThrows(IllegalArgumentException.class, () -> new PartyProjection(" ", "{}", now));
    }
}
