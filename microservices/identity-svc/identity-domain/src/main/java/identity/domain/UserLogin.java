package identity.domain;

import java.util.Set;

/**
 * A credential principal (P1-F1-S2). Carries no framework types; the hashed
 * password travels in its OFBiz HashCrypt wire format (never the raw value).
 */
public record UserLogin(String userLoginId, String partyId, String currentPassword, boolean enabled,
        Set<String> roles) {

    public static final String ROLE_USER = "USER";

    public UserLogin {
        if (userLoginId == null || userLoginId.isBlank()) {
            throw new IllegalArgumentException("userLoginId must not be blank");
        }
        roles = roles == null || roles.isEmpty() ? Set.of(ROLE_USER) : Set.copyOf(roles);
    }

    /** Credentials created through the admin API are plain users. */
    public UserLogin(String userLoginId, String partyId, String currentPassword, boolean enabled) {
        this(userLoginId, partyId, currentPassword, enabled, Set.of(ROLE_USER));
    }
}
