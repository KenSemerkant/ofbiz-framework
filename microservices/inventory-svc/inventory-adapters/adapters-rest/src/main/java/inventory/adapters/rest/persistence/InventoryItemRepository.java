package inventory.adapters.rest.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryItemRepository extends JpaRepository<InventoryItemEntity, String> {

    /** Stable row order (P3-F2-S2): reservation picks the first sufficient item. */
    List<InventoryItemEntity> findByProductIdAndFacilityIdOrderByInventoryItemIdAsc(
        String productId, String facilityId);
}
