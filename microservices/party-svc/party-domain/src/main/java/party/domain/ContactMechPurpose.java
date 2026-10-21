package party.domain;

/**
 * Purpose of a contact mechanism (OFBiz ContactMechPurposeType slice).
 * Each purpose is only valid for one mech type — linking with an
 * incompatible purpose is a domain error (P2-F1-S2 AC).
 */
public enum ContactMechPurpose {
    PRIMARY_EMAIL(ContactMechType.EMAIL_ADDRESS),
    BILLING_EMAIL(ContactMechType.EMAIL_ADDRESS),
    PHONE_WORK(ContactMechType.TELECOM_NUMBER),
    PHONE_HOME(ContactMechType.TELECOM_NUMBER),
    SHIPPING_ADDRESS(ContactMechType.POSTAL_ADDRESS),
    BILLING_ADDRESS(ContactMechType.POSTAL_ADDRESS);

    private final ContactMechType mechType;

    ContactMechPurpose(ContactMechType mechType) {
        this.mechType = mechType;
    }

    public ContactMechType mechType() {
        return mechType;
    }

    public void validateAgainst(ContactMechType type) {
        if (this.mechType != type) {
            throw new IllegalArgumentException(
                "purpose " + name() + " requires a " + mechType + ", got " + type);
        }
    }
}
