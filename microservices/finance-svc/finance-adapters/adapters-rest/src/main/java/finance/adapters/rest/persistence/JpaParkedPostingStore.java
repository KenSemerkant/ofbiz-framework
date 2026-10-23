package finance.adapters.rest.persistence;

import java.util.List;
import org.springframework.stereotype.Repository;
import finance.application.ParkedPostingStore;

/** JPA adapter for the ParkedPostingStore port. */
@Repository
public class JpaParkedPostingStore implements ParkedPostingStore {

    private final ParkedPostingRepository parked;

    public JpaParkedPostingStore(ParkedPostingRepository parked) {
        this.parked = parked;
    }

    @Override
    public void park(String eventType, String aggregateKey, String reason, String payloadJson) {
        parked.save(new ParkedPostingEntity(eventType, aggregateKey, reason, payloadJson));
    }

    @Override
    public List<String> parkedReasons() {
        return parked.findAll().stream()
            .map(e -> "%s %s: %s".formatted(e.getEventType(), e.getAggregateKey(),
                e.getReason()))
            .toList();
    }
}
