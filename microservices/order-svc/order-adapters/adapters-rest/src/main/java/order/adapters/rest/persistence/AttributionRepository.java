package order.adapters.rest.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import order.adapters.rest.persistence.SmallStores.AttributionEntity;

public interface AttributionRepository extends JpaRepository<AttributionEntity, String> {

    long countByTrackingCode(String trackingCode);
}
