package order.adapters.rest.persistence;

import java.util.List;
import order.application.OrderSerializer;
import order.domain.Order;
import order.domain.OrderLine;
import order.domain.ReturnOrder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Event payloads as JSON. The placed payload carries the OrderPlaced.avsc
 * field names (orderId, customerId, placedAt, grandTotal, currency,
 * lineCount) plus trackingCode/lines for the downstream saga consumers —
 * a backward-compatible superset.
 */
public class JacksonOrderSerializer implements OrderSerializer {

    private final ObjectMapper json = new ObjectMapper();

    @Override
    public String serializePlaced(Order order) {
        ObjectNode node = json.createObjectNode();
        node.put("orderId", order.orderId());
        node.put("customerId", order.customerId());
        node.put("placedAt", order.history().isEmpty() ? null
            : order.history().get(0).changedAt().toString());
        node.put("grandTotal", order.grandTotal().toPlainString());
        node.put("currency", order.lines().get(0).currencyUomId());
        node.put("lineCount", order.lines().size());
        if (order.trackingCode() != null) {
            node.put("trackingCode", order.trackingCode());
        }
        node.set("lines", json.valueToTree(order.lines().stream()
            .map(line -> new OrderJson.LineDto(line.orderItemSeqId(), line.productId(),
                line.facilityId(), line.quantity(), line.unitPrice(), line.currencyUomId()))
            .toList()));
        return node.toString();
    }

    @Override
    public String serializeStatusChange(Order order, String note) {
        ObjectNode node = json.createObjectNode();
        node.put("orderId", order.orderId());
        node.put("customerId", order.customerId());
        node.put("status", order.status().name());
        if (note != null) {
            node.put("note", note);
        }
        node.put("grandTotal", order.grandTotal().toPlainString());
        node.put("currency", order.lines().get(0).currencyUomId());
        return node.toString();
    }

    @Override
    public String serializeOrder(Order order) {
        return serializePlaced(order);
    }

    @Override
    public String serializeLineNode(List<OrderLine> lines) {
        return json.writeValueAsString(lines.stream()
            .map(line -> new OrderJson.LineDto(line.orderItemSeqId(), line.productId(),
                line.facilityId(), line.quantity(), line.unitPrice(), line.currencyUomId()))
            .toList());
    }

    @Override
    public String serializeReturn(ReturnOrder returns) {
        ObjectNode node = json.createObjectNode();
        node.put("returnId", returns.returnId());
        node.put("orderId", returns.orderId());
        node.put("status", returns.status().name());
        node.set("lines", json.valueToTree(returns.lines().stream()
            .map(line -> new OrderJson.ReturnLineDto(line.productId(), line.facilityId(),
                line.quantity()))
            .toList()));
        return node.toString();
    }

    @Override
    public String serializeInvoiceRequest(Order order) {
        ObjectNode node = json.createObjectNode();
        node.put("orderId", order.orderId());
        node.put("customerId", order.customerId());
        node.put("grandTotal", order.grandTotal().toPlainString());
        node.put("currency", order.lines().get(0).currencyUomId());
        node.put("lineCount", order.lines().size());
        return node.toString();
    }
}
