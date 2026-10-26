package inventory.application;

import java.util.Optional;
import inventory.domain.Shipment;

/** Persistence port for the Shipment aggregate. */
public interface ShipmentStore {

    Optional<Shipment> find(String shipmentId);

    /** P9-F2: shipment listing for the SPA catalog area. */
    java.util.List<Shipment> all();

    void save(Shipment shipment);
}
