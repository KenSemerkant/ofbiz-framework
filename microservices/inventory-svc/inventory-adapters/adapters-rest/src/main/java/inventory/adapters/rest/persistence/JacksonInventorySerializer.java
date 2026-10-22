package inventory.adapters.rest.persistence;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import inventory.application.InventorySerializer;
import inventory.domain.Facility;
import inventory.domain.InventoryItem;
import inventory.domain.Reservation;
import inventory.domain.Shipment;

/**
 * Event payload shapes consumed by order-svc and future fulfillment readers.
 * Every payload goes through Jackson so user-supplied quotes/backslashes are
 * escaped (quality review: no hand-built JSON).
 */
public class JacksonInventorySerializer implements InventorySerializer {

    private final tools.jackson.databind.ObjectMapper mapper =
        new tools.jackson.databind.ObjectMapper();

    @Override
    public String serializeItem(InventoryItem item) {
        return mapper.writeValueAsString(itemFields(item));
    }

    @Override
    public String serializeFacility(Facility facility) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("facilityId", facility.facilityId());
        fields.put("facilityName", facility.facilityName());
        fields.put("facilityTypeId", facility.facilityTypeId());
        return mapper.writeValueAsString(fields);
    }

    @Override
    public String serializeFacilityDeleted(String facilityId) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("facilityId", facilityId);
        return mapper.writeValueAsString(fields);
    }

    @Override
    public String serializeStockChange(InventoryItem item, BigDecimal quantity) {
        var fields = itemFields(item);
        fields.put("quantity", quantity);
        return mapper.writeValueAsString(fields);
    }

    @Override
    public String serializeTransfer(InventoryItem from, InventoryItem to, BigDecimal quantity) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("from", itemFields(from));
        fields.put("to", itemFields(to));
        fields.put("quantity", quantity);
        return mapper.writeValueAsString(fields);
    }

    @Override
    public String serializeReservation(Reservation reservation, InventoryItem item) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("reservationId", reservation.reservationId());
        fields.put("orderId", reservation.orderId());
        fields.put("orderItemSeqId", reservation.orderItemSeqId());
        fields.put("productId", reservation.productId());
        fields.put("facilityId", reservation.facilityId());
        fields.put("inventoryItemId", reservation.inventoryItemId());
        fields.put("quantity", reservation.quantity());
        fields.put("status", reservation.status().name());
        fields.put("item", itemFields(item));
        return mapper.writeValueAsString(fields);
    }

    @Override
    public String serializeShipment(Shipment shipment) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("shipmentId", shipment.shipmentId());
        fields.put("orderId", shipment.orderId());
        fields.put("status", shipment.status().name());
        fields.put("items", shipment.items());
        return mapper.writeValueAsString(fields);
    }

    private LinkedHashMap<String, Object> itemFields(InventoryItem item) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("inventoryItemId", item.inventoryItemId());
        fields.put("productId", item.productId());
        fields.put("facilityId", item.facilityId());
        fields.put("serialNumber", item.serialNumber());
        fields.put("quantityOnHandTotal", item.quantityOnHandTotal());
        fields.put("availableToPromiseTotal", item.availableToPromiseTotal());
        return fields;
    }
}
