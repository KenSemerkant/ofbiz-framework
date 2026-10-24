package marketing.adapters.rest.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttributionRepository extends JpaRepository<AttributionEntity, String> {

    List<AttributionEntity> findByTrackingCodeOrderByOrderIdAsc(String trackingCode);
}
