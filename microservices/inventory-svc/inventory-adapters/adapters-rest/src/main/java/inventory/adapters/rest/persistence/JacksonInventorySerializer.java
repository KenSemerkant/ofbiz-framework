package inventory.adapters.rest.persistence;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import inventory.application.InventorySerializer;
import inventory.domain.Facility;
import inventory.domain.InventoryItem;

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
