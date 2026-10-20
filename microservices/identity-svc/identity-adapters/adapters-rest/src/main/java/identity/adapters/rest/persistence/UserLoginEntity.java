package identity.adapters.rest.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "user_logins")
public class UserLoginEntity {

    @Id
    private String userLoginId;

    private String partyId;

    private String currentPassword;

    private boolean enabled = true;

    /** Comma-separated role names (ROLE_ prefix is added by Spring Security). */
    private String roles = "USER";

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    protected UserLoginEntity() {}

    public UserLoginEntity(String userLoginId, String partyId, String currentPassword, boolean enabled,
            Set<String> roles) {
        this.userLoginId = userLoginId;
        this.partyId = partyId;
        this.currentPassword = currentPassword;
        this.enabled = enabled;
        setRoles(roles);
    }

    public String getUserLoginId() {
        return userLoginId;
    }

    public String getPartyId() {
        return partyId;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Set<String> getRoles() {
        return Arrays.stream(roles.split(","))
            .map(String::trim)
            .filter(role -> !role.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }

    private void setRoles(Set<String> roles) {
        this.roles = roles == null || roles.isEmpty() ? "USER" : String.join(",", roles);
    }
}
