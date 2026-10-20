package identity.adapters.rest.security;

import identity.domain.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Bridges the domain hasher into Spring Security's PasswordEncoder. */
public final class HashCryptPasswordEncoder implements PasswordEncoder {

    private final PasswordHasher hasher;

    public HashCryptPasswordEncoder(PasswordHasher hasher) {
        this.hasher = hasher;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return hasher.hash(rawPassword.toString());
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        // Authorization-server client secrets use Boot's {noop} prefix in the
        // dev profile; user passwords use OFBiz HashCrypt formats.
        if (encodedPassword != null && encodedPassword.startsWith("{noop}")) {
            byte[] raw = rawPassword.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] expected = encodedPassword.substring(6)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return java.security.MessageDigest.isEqual(raw, expected);
        }
        return hasher.matches(rawPassword.toString(), encodedPassword);
    }
}
