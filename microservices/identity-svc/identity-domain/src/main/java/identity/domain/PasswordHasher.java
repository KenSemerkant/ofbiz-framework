package identity.domain;

/**
 * Hashes and verifies credentials. Implementations must be compatible with the
 * monolith's HashCrypt formats (P1-F1-S2): salted PBKDF2 ({PBKDF2-SHA256}...)
 * and legacy salted SHA ($SHA$salt$hash).
 */
public interface PasswordHasher {

    /** Produces the stored representation of a raw password (new credentials). */
    String hash(String rawPassword);

    /** Verifies a raw password against any supported stored format. */
    boolean matches(String rawPassword, String storedPassword);
}
