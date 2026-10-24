package humanres.adapters.rest.persistence;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import humanres.application.PartyProjectionStore;

/** JPA adapter for the PartyProjectionStore port. */
@Repository
public class JpaPartyProjectionStore implements PartyProjectionStore {

    private final PartyProjectionRepository projections;

    public JpaPartyProjectionStore(PartyProjectionRepository projections) {
        this.projections = projections;
    }

    @Override
    public void upsert(String partyId, String namesJson) {
        projections.save(new PartyProjectionEntity(partyId, namesJson));
    }

    @Override
    public Optional<String> find(String partyId) {
        return projections.findById(partyId).map(PartyProjectionEntity::getNames);
    }
}
