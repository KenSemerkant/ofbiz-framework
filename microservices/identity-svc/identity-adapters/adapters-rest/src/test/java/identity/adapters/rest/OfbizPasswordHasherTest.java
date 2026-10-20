package identity.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import identity.adapters.rest.security.OfbizPasswordHasher;
import org.junit.jupiter.api.Test;

/**
 * P1-F1-S2 AC2: hashing is compatible with the monolith's HashCrypt formats.
 * Vectors are independently precomputed (not produced by this code):
 * - PBKDF2-SHA256, 10000 iterations, salt "testsalt", 256-bit key.
 * - Legacy salted SHA-1: $SHA$abcsalt$b64url(sha1("abcsalt" + "opensecret")).
 */
class OfbizPasswordHasherTest {

    private final OfbizPasswordHasher hasher = new OfbizPasswordHasher();

    @Test
    void verifiesMonolithPbkdf2Vector() {
        assertTrue(hasher.matches("test-password",
            "{PBKDF2-SHA256}10000$dGVzdHNhbHQ=$x0nPrTYVVaKd1Y70oTIvgB3W2shyi792oM5QIVXv+Ww="));
    }

    @Test
    void verifiesMonolithSaltedShaVector() {
        assertTrue(hasher.matches("opensecret", "$SHA$abcsalt$Ckf-LzsCPPaU9aKFwpmFM1KKtng"));
    }

    @Test
    void rejectsWrongPasswordInBothFormats() {
        assertFalse(hasher.matches("wrong", "{PBKDF2-SHA256}10000$dGVzdHNhbHQ=$x0nPrTYVVaKd1Y70oTIvgB3W2shyi792oM5QIVXv+Ww="));
        assertFalse(hasher.matches("wrong", "$SHA$abcsalt$Ckf-LzsCPPaU9aKFwpmFM1KKtng"));
    }

    @Test
    void hashRoundTripsAndUsesPbkdf2Format() {
        String stored = hasher.hash("another-password");
        assertTrue(stored.startsWith("{PBKDF2-SHA256}10000$"), "new credentials use PBKDF2: " + stored);
        assertTrue(hasher.matches("another-password", stored));
        assertFalse(hasher.matches("other", stored));
    }
}
