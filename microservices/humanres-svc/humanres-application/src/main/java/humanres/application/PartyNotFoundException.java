package humanres.application;

/** P6-T3: onboarding requires a projected party — unknown party is a 404. */
public final class PartyNotFoundException extends RuntimeException {

    public PartyNotFoundException(String partyId) {
        super("no party %s in the humanres projection".formatted(partyId));
    }
}
