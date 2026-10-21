package party.domain;

/**
 * A contact mechanism (OFBiz ContactMech) with type-specific value format
 * validation — invalid values are rejected at the trust boundary (P2-F1-S2).
 */
public record ContactMech(String contactMechId, ContactMechType type, String value) {

    public ContactMech {
        if (contactMechId == null || contactMechId.isBlank()) {
            throw new IllegalArgumentException("contactMechId must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        switch (type) {
            case EMAIL_ADDRESS -> require(value.contains("@") && !value.startsWith("@")
                && !value.endsWith("@"), "malformed email: " + value);
            case TELECOM_NUMBER -> require(value.matches("\\+?[0-9][0-9 ()-]{4,}"),
                "malformed phone number: " + value);
            case POSTAL_ADDRESS -> { /* any non-blank text is a postal address */ }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
