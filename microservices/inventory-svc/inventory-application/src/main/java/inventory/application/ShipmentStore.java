package inventory.application;

import java.util.Optional;
import inventory.domain.Shipment;

/** Persistence port for the Shipment aggregate. */
public interface ShipmentStore {

    Optional<Shipment> find(String shipmentId);

    void save(Shipment shipment);
}
