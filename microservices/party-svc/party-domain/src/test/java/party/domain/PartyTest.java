package party.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** P2-F1-S1 aggregate contract: the AC gates (roles, relationships, guards). */
class PartyTest {

    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void personPartyCarriesPersonNotGroup() {
        Party party = Party.person("P1", new Person("Ada", "Lovelace"));
        assertEquals(PartyKind.PERSON, party.kind());
        assertEquals("Ada Lovelace", party.person().displayName());
        assertEquals("P1", party.partyId());
    }

    @Test
    void groupPartyCarriesGroupNotPerson() {
        Party party = Party.group("G1", new PartyGroup("Acme Corp"));
        assertEquals(PartyKind.PARTY_GROUP, party.kind());
        assertEquals("Acme Corp", party.group().groupName());
    }

    @Test
    void blankIdOrMissingKindRejected() {
        assertThrows(IllegalArgumentException.class, () -> Party.person("", new Person("A", "B")));
        assertThrows(IllegalArgumentException.class,
            () -> new Party(null, null, null, null, null, null));
    }

    @Test
    void personKindRequiresPersonAndGroupKindRequiresGroup() {
        assertThrows(IllegalArgumentException.class,
            () -> new Party("P1", PartyKind.PERSON, null, new PartyGroup("X"), null, null));
        assertThrows(IllegalArgumentException.class,
            () -> new Party("G1", PartyKind.PARTY_GROUP, new Person("A", "B"), null, null, null));
    }

    @Test
    void grantRoleReportsChangeAndGuardsDuplicates() {
        Party party = Party.person("P1", new Person("A", "B"));
        assertTrue(party.grantRole("CUSTOMER"));
        assertFalse(party.grantRole("CUSTOMER"));
        assertTrue(party.hasRole("CUSTOMER"));
        assertThrows(IllegalArgumentException.class, () -> party.grantRole(" "));
    }

    @Test
    void revokeRoleReportsChange() {
        Party party = Party.person("P1", new Person("A", "B"));
        party.grantRole("CUSTOMER");
        assertTrue(party.revokeRole("CUSTOMER"));
        assertFalse(party.revokeRole("CUSTOMER"));
        assertThrows(IllegalArgumentException.class, () -> party.revokeRole(null));
    }

    @Test
    void relationshipLifecycle() {
        Party party = Party.person("P1", new Person("A", "B"));
        PartyRelationship rel = new PartyRelationship("P2", "EMPLOYMENT", FROM, null);
        assertTrue(party.relateTo(rel));
        assertFalse(party.relateTo(rel));
        assertEquals(List.of(rel), party.activeRelationships(FROM));
        assertThrows(IllegalArgumentException.class, () -> party.relateTo(null));
        assertThrows(IllegalArgumentException.class,
            () -> party.relateTo(new PartyRelationship("P1", "X", FROM, null)));
    }

    @Test
    void returnedCollectionsAreDefensiveCopies() {
        Party party = Party.person("P1", new Person("A", "B"));
        party.grantRole("CUSTOMER");
        assertThrows(UnsupportedOperationException.class, () -> party.roles().add("ADMIN"));
        assertThrows(UnsupportedOperationException.class,
            () -> party.relationships().add(new PartyRelationship("P2", "X", FROM, null)));
    }
}
