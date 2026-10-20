package identity.application;

import identity.domain.PasswordHasher;
import identity.domain.UserLogin;

/**
 * P1-F1-S2: user CRUD. New credentials are hashed with the PBKDF2 format;
 * verification accepts every monolith HashCrypt format so migrated
 * user_login rows keep working.
 */
public class UserAdministrationService {

    private final UserLoginStore store;
    private final PasswordHasher hasher;

    public UserAdministrationService(UserLoginStore store, PasswordHasher hasher) {
        this.store = store;
        this.hasher = hasher;
    }

    public UserLogin create(String userLoginId, String rawPassword, String partyId) {
        if (userLoginId == null || userLoginId.isBlank()) {
            throw new IllegalArgumentException("userLoginId must not be blank");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
        if (store.exists(userLoginId)) {
            throw new DuplicateUserException(userLoginId);
        }
        UserLogin user = new UserLogin(userLoginId, partyId, hasher.hash(rawPassword), true);
        store.save(user);
        return user;
    }

    /** Public view — never exposes the hashed password. */
    public record UserView(String userLoginId, String partyId, boolean enabled) {}

    public UserView view(String userLoginId) {
        return store.find(userLoginId)
            .map(u -> new UserView(u.userLoginId(), u.partyId(), u.enabled()))
            .orElseThrow(() -> new UserNotFoundException(userLoginId));
    }

    public static class DuplicateUserException extends RuntimeException {
        public DuplicateUserException(String userLoginId) {
            super("userLoginId already exists: " + userLoginId);
        }
    }

    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String userLoginId) {
            super("userLoginId not found: " + userLoginId);
        }
    }
}
