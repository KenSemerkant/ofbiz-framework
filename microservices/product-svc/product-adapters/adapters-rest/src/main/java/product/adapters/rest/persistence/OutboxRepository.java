package product.adapters.rest.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<OutboxEntryEntity, Long> {

    @Query("select o from OutboxEntryEntity o where o.publishedAt is null order by o.id asc")
    List<OutboxEntryEntity> findUnpublished();
}
