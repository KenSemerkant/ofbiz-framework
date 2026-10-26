package party.application;

import java.util.Optional;
import party.domain.Party;

/** Persistence port for the Party aggregate. */
public interface PartyStore {

    Optional<Party> find(String partyId);

    void save(Party party);

    /** P2-F1-S3: paged role lookup, ordered by party id for stable pages. */
    PartyPage findByRole(String roleTypeId, int page, int size);

    /** P9-F4: paged listing of all parties (role filter optional in the SPA). */
    PartyPage allPaged(int page, int size);
}
