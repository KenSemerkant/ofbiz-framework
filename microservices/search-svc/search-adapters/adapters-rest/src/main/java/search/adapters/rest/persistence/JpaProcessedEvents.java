package search.adapters.rest.persistence;

import org.springframework.stereotype.Repository;
import search.application.ProcessedEvents;

@Repository
public class JpaProcessedEvents implements ProcessedEvents {

    private final ProcessedEventRepository processedEvents;

    public JpaProcessedEvents(ProcessedEventRepository processedEvents) {
        this.processedEvents = processedEvents;
    }

    @Override
    public boolean seen(String eventId) {
        return processedEvents.existsById(eventId);
    }

    @Override
    public void record(String eventId) {
        processedEvents.save(new ProcessedEventEntity(eventId));
    }
}
