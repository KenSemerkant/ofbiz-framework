package party.application;

import java.util.Optional;
import party.domain.Party;

/** Persistence port for the Party aggregate. */
public interface PartyStore {

    Optional<Party> find(String partyId);

    void save(Party party);
}
