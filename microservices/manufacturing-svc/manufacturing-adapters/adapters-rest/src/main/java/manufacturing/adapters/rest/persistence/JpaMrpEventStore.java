package manufacturing.adapters.rest.persistence;

import java.util.List;
import org.springframework.stereotype.Repository;
import manufacturing.application.MrpEventStore;
import manufacturing.domain.MrpEvent;

/** JPA adapter for the MrpEventStore port. */
@Repository
public class JpaMrpEventStore implements MrpEventStore {

    private final MrpEventRepository events;

    public JpaMrpEventStore(MrpEventRepository events) {
        this.events = events;
    }

    @Override
    public void save(MrpEvent event) {
        events.save(new MrpEventEntity(event.eventId(), event.productId(), event.eventType(),
            event.quantity(), event.occurredAt()));
    }

    @Override
    public List<MrpEvent> all() {
        return events.findAll().stream().map(this::toDomain).toList();
    }

    private MrpEvent toDomain(MrpEventEntity entity) {
        return new MrpEvent(entity.getEventId(), entity.getProductId(), entity.getEventType(),
            entity.getQuantity(), entity.getOccurredAt());
    }
}
