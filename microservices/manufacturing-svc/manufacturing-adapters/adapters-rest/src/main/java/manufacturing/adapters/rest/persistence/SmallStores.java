package manufacturing.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import manufacturing.application.ProcessedEventStore;

/** processed_events: consumer idempotency key/value store. */
public final class SmallStores {

    @Entity
    @Table(name = "processed_events")
    public static class ProcessedEventEntity {

        @Id
        @Column(name = "event_id")
        private String eventId;
    }

    @Repository
    public static class JdbcProcessedEventStore implements ProcessedEventStore {

        private final JdbcTemplate db;

        public JdbcProcessedEventStore(JdbcTemplate db) {
            this.db = db;
        }

        @Override
        public boolean recordIfAbsent(String eventId) {
            try {
                db.update("insert into processed_events (event_id) values (?)", eventId);
                return true;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                return false;
            }
        }
    }

    private SmallStores() {
    }
}
