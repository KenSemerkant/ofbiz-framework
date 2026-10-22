package inventory.application;

import inventory.domain.Facility;
import inventory.domain.InventoryItem;
import inventory.domain.Reservation;
import java.math.BigDecimal;

/**
 * Event payload building port. Every payload goes through a Jackson
 * implementation (quality-review rule: no hand-built JSON, user input gets
 * escaped).
 */
public interface InventorySerializer {

    String serializeItem(InventoryItem item);

    String serializeFacility(Facility facility);

    String serializeFacilityDeleted(String facilityId);

    /** Stock ops share one payload shape: the delta plus the after-snapshot. */
    String serializeStockChange(InventoryItem item, BigDecimal quantity);

    String serializeTransfer(InventoryItem from, InventoryItem to, BigDecimal quantity);

    /** Reservation lifecycle events: the reservation plus its item snapshot. */
    String serializeReservation(Reservation reservation, InventoryItem item);
}
