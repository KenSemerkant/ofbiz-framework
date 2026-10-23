package order.application;

import java.util.List;
import order.domain.Order;
import order.domain.OrderLine;
import order.domain.ReturnOrder;

/**
 * Serialization port: the application layer emits event payloads but stays
 * free of JSON framework types (ADR-007); the adapter layer supplies this.
 */
public interface OrderSerializer {

    /** order.order.placed payload — field names match contracts/order/v1/OrderPlaced.avsc. */
    String serializePlaced(Order order);

    /** order.order.approved/completed/cancelled payload: {"orderId","status",...}. */
    String serializeStatusChange(Order order, String note);

    String serializeOrder(Order order);

    String serializeLineNode(List<OrderLine> lines);

    String serializeReturn(ReturnOrder returns);

    /** order.order.invoicerequested payload. */
    String serializeInvoiceRequest(Order order);
}
