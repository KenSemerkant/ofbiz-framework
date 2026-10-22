package inventory.adapters.rest.persistence;

import java.util.ArrayList;
import java.util.List;
import inventory.domain.ShipmentItem;
import tools.jackson.databind.ObjectMapper;

/**
 * JSON mapping for the inline items column and the shipment event payload
 * (same shape both places, P3-F2-S3 — mirrors product-svc's inline JSON).
 */
public final class ShipmentJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ShipmentJson() {
    }

    public static String serializeItems(List<ShipmentItem> items) {
        return MAPPER.writeValueAsString(items);
    }

    public static List<ShipmentItem> deserializeItems(String json) {
        return MAPPER.readValue(json,
            MAPPER.getTypeFactory().constructCollectionType(ArrayList.class, ShipmentItem.class));
    }
}
