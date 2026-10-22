package inventory.adapters.rest.persistence;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import inventory.application.ShipmentStore;
import inventory.domain.Shipment;

/** JPA adapter for the ShipmentStore port. */
@Repository
public class JpaShipmentStore implements ShipmentStore {

    private final ShipmentRepository shipments;

    public JpaShipmentStore(ShipmentRepository shipments) {
        this.shipments = shipments;
    }

    @Override
    public Optional<Shipment> find(String shipmentId) {
        return shipments.findById(shipmentId).map(ShipmentEntity::toDomain);
    }

    @Override
    public void save(Shipment shipment) {
        ShipmentEntity entity = shipments.findById(shipment.shipmentId())
            .orElseGet(() -> new ShipmentEntity(shipment.shipmentId()));
        entity.applyFrom(shipment);
        shipments.save(entity);
    }
}
