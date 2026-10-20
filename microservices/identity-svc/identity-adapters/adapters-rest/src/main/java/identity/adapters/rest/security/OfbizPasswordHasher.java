package identity.adapters.rest.security;

import identity.domain.PasswordHasher;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Monolith HashCrypt-compatible hashing (P1-F1-S2). Produces the PBKDF2-SHA256
 * format for new credentials and verifies every format the monolith writes:
 * {@code {PBKDF2-SHA256}iter$b64(salt)$b64(hash)} and legacy
 * {@code $SHA$salt$b64url(digest(salt+password))}.
 */
public final class OfbizPasswordHasher implements PasswordHasher {

    private static final int ITERATIONS = 10_000;
    private static final int KEY_BITS = 256;
    private static final String PBKDF2_PREFIX = "{PBKDF2-";
    private final SecureRandom random = new SecureRandom();

    @Override
    public String hash(String rawPassword) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        try {
            return "{PBKDF2-SHA256}" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(
                    pbkdf2("PBKDF2WithHmacSHA256", rawPassword, salt, ITERATIONS));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 unavailable", e);
        }
    }

    @Override
    public boolean matches(String rawPassword, String storedPassword) {
        try {
            if (storedPassword != null && storedPassword.startsWith(PBKDF2_PREFIX)) {
                return matchesPbkdf2(rawPassword, storedPassword);
            }
            if (storedPassword != null && storedPassword.startsWith("$")) {
                return matchesSaltedDigest(rawPassword, storedPassword);
            }
            return false;
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private boolean matchesPbkdf2(String raw, String stored) throws GeneralSecurityException {
        // {PBKDF2-<variant>}iterations$b64(salt)$b64(hash)
        int typeEnd = stored.indexOf('}');
        String variant = stored.substring(PBKDF2_PREFIX.length(), typeEnd); // SHA1 | SHA256 | SHA384 | SHA512
        String[] parts = stored.substring(typeEnd + 1).split("\\$");
        if (parts.length != 3) {
            return false;
        }
        int iterations = Integer.parseInt(parts[0]);
        byte[] salt = Base64.getDecoder().decode(parts[1]);
        byte[] expected = Base64.getDecoder().decode(parts[2]);
        byte[] actual = pbkdf2("PBKDF2WithHmac" + variant, raw, salt, iterations);
        return MessageDigest.isEqual(expected, actual);
    }

    private boolean matchesSaltedDigest(String raw, String stored) throws GeneralSecurityException {
        // $<hashType>$<salt>$<b64url(digest(salt+password))>
        int typeEnd = stored.indexOf('$', 1);
        int saltEnd = stored.indexOf('$', typeEnd + 1);
        if (typeEnd < 0 || saltEnd < 0) {
            return false;
        }
        String hashType = stored.substring(1, typeEnd);
        String salt = stored.substring(typeEnd + 1, saltEnd);
        byte[] expected = Base64.getUrlDecoder().decode(stored.substring(saltEnd + 1));
        MessageDigest digest = MessageDigest.getInstance(hashType);
        digest.update(salt.getBytes(StandardCharsets.UTF_8));
        byte[] actual = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] pbkdf2(String algorithm, String raw, byte[] salt, int iterations)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(raw.toCharArray(), salt, iterations, KEY_BITS);
        return SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded();
    }
}
