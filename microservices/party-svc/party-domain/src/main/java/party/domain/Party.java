package party.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Party aggregate root (P2-F1-S1): person or group, its roles and outbound
 * relationships. Mutators return whether state changed so the application
 * layer can emit the matching domain event exactly once.
 */
public final class Party {

    private final String partyId;
    private final PartyKind kind;
    private final Person person;
    private final PartyGroup group;
    private final Set<String> roles = new LinkedHashSet<>();
    private final List<PartyRelationship> relationships = new ArrayList<>();

    public Party(String partyId, PartyKind kind, Person person, PartyGroup group,
            Collection<String> roles, Collection<PartyRelationship> relationships) {
        if (partyId == null || partyId.isBlank()) {
            throw new IllegalArgumentException("partyId must not be blank");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (kind == PartyKind.PERSON && person == null) {
            throw new IllegalArgumentException("PERSON party requires a Person");
        }
        if (kind == PartyKind.PARTY_GROUP && group == null) {
            throw new IllegalArgumentException("PARTY_GROUP party requires a PartyGroup");
        }
        this.partyId = partyId;
        this.kind = kind;
        this.person = person;
        this.group = group;
        if (roles != null) {
            this.roles.addAll(roles);
        }
        if (relationships != null) {
            this.relationships.addAll(relationships);
        }
    }

    public static Party person(String partyId, Person person) {
        return new Party(partyId, PartyKind.PERSON, person, null, null, null);
    }

    public static Party group(String partyId, PartyGroup group) {
        return new Party(partyId, PartyKind.PARTY_GROUP, null, group, null, null);
    }

    /** @return true if the role was not already present. */
    public boolean grantRole(String roleTypeId) {
        if (roleTypeId == null || roleTypeId.isBlank()) {
            throw new IllegalArgumentException("roleTypeId must not be blank");
        }
        return roles.add(roleTypeId);
    }

    /** @return true if the role was present and is now removed. */
    public boolean revokeRole(String roleTypeId) {
        if (roleTypeId == null || roleTypeId.isBlank()) {
            throw new IllegalArgumentException("roleTypeId must not be blank");
        }
        return roles.remove(roleTypeId);
    }

    /** @return true if an equivalent relationship was not already present. */
    public boolean relateTo(PartyRelationship relationship) {
        if (relationship == null) {
            throw new IllegalArgumentException("relationship must not be null");
        }
        if (relationship.toPartyId().equals(partyId)) {
            throw new IllegalArgumentException("a party cannot relate to itself");
        }
        if (relationships.contains(relationship)) {
            return false;
        }
        return relationships.add(relationship);
    }

    public boolean hasRole(String roleTypeId) {
        return roles.contains(roleTypeId);
    }

    public List<PartyRelationship> activeRelationships(Instant at) {
        return relationships.stream().filter(r -> r.activeAt(at)).toList();
    }

    public String partyId() {
        return partyId;
    }

    public PartyKind kind() {
        return kind;
    }

    public Person person() {
        return person;
    }

    public PartyGroup group() {
        return group;
    }

    public Set<String> roles() {
        return Set.copyOf(roles);
    }

    public List<PartyRelationship> relationships() {
        return List.copyOf(relationships);
    }
}
