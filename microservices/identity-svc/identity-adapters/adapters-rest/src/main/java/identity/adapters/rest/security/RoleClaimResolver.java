package identity.adapters.rest.security;

import identity.application.PartyProjectionStore;
import identity.application.UserLoginStore;
import identity.domain.UserLogin;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * P2-F3-S1: resolves token roles as the union of the user's own roles and
 * the roles carried by the party projection (refreshed from
 * `party.party.*` / `party.role.*` events within the consumer's <5s lag —
 * PartyProjectionConsumerTest). A revoked role disappears from the
 * projection and therefore from the next token issued.
 */
@Component
public class RoleClaimResolver {

    private final UserLoginStore users;
    private final PartyProjectionStore projections;
    private final ObjectMapper json = new ObjectMapper();

    public RoleClaimResolver(UserLoginStore users, PartyProjectionStore projections) {
        this.users = users;
        this.projections = projections;
    }

    /** @return the roles claim for the given principal, empty for unknown. */
    public Set<String> resolve(String principalName) {
        if (principalName == null || principalName.isBlank()) {
            return Set.of();
        }
        Optional<UserLogin> user = users.find(principalName);
        if (user.isEmpty()) {
            return Set.of(); // machine principal (client_credentials) — no roles
        }
        Set<String> roles = new HashSet<>(user.get().roles());
        String partyId = user.get().partyId();
        if (partyId == null || partyId.isBlank()) {
            return roles; // user not linked to a party — own roles only
        }
        projections.find(partyId).ifPresent(projection -> {
            JsonNode payload = json.readTree(projection.payload());
            payload.path("roles").forEach(role -> roles.add(role.asText()));
        });
        return roles;
    }

    public Optional<String> partyIdOf(String principalName) {
        return users.find(principalName).map(UserLogin::partyId);
    }
}
