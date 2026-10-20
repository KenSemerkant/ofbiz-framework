package identity.adapters.rest.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "processed_events")
public class ProcessedEventEntity {

    @Id
    private String eventId;

    private Instant processedAt = Instant.now();

    protected ProcessedEventEntity() {}

    public ProcessedEventEntity(String eventId) {
        this.eventId = eventId;
    }
}
