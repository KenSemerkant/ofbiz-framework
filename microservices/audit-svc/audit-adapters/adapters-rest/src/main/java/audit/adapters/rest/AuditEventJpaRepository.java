package audit.adapters.rest;

import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventJpaRepository extends JpaRepository<AuditEventEntity, AuditEventEntityId> {

    @Query("select e from AuditEventEntity e where e.topic = :topic"
        + " and e.id.occurredAt between :from and :to")
    Page<AuditEventEntity> findByTopicAndTime(@Param("topic") String topic,
        @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to, Pageable pageable);

    @Query("select e from AuditEventEntity e where e.id.occurredAt between :from and :to")
    Page<AuditEventEntity> findByTime(@Param("from") OffsetDateTime from,
        @Param("to") OffsetDateTime to, Pageable pageable);
}
