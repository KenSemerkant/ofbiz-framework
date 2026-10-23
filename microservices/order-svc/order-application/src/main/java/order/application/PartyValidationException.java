package order.application;

/** Saga rejection (P4-F2-S1): the placing party does not exist. */
public class PartyValidationException extends RuntimeException {

    public PartyValidationException(String partyId) {
        super("unknown party " + partyId);
    }
}
