package party.domain;

import java.util.Set;

/** A contact mechanism linked to a party, with its purposes. */
public record PartyContactMech(ContactMech mech, Set<ContactMechPurpose> purposes) {

    public PartyContactMech {
        if (mech == null) {
            throw new IllegalArgumentException("mech must not be null");
        }
        purposes = purposes == null ? Set.of() : Set.copyOf(purposes);
        purposes.forEach(purpose -> purpose.validateAgainst(mech.type()));
    }
}
