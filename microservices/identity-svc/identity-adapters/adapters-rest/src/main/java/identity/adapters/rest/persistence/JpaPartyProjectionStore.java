package identity.adapters.rest.persistence;

import identity.application.PartyProjectionStore;
import identity.domain.PartyProjection;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JpaPartyProjectionStore implements PartyProjectionStore {

    private final PartyProjectionRepository projections;
    private final ProcessedEventRepository processedEvents;
    private final TransactionTemplate transactions;

    public JpaPartyProjectionStore(PartyProjectionRepository projections,
            ProcessedEventRepository processedEvents, TransactionTemplate transactions) {
        this.projections = projections;
        this.processedEvents = processedEvents;
        this.transactions = transactions;
    }

    @Override
    public boolean alreadyProcessed(String eventId) {
        return processedEvents.existsById(eventId);
    }

    @Override
    public void upsert(PartyProjection projection, String eventId) {
        transactions.executeWithoutResult(tx -> {
            projections.save(new PartyProjectionEntity(projection.partyId(),
                projection.payload(), projection.updatedAt()));
            processedEvents.save(new ProcessedEventEntity(eventId));
        });
    }

    @Override
    public Optional<PartyProjection> find(String partyId) {
        return projections.findById(partyId)
            .map(e -> new PartyProjection(e.getPartyId(), e.getPayload(), e.getUpdatedAt()));
    }
}
