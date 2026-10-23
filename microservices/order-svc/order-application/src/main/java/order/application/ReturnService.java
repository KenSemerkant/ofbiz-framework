package order.application;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import order.domain.Order;
import order.domain.OrderStatus;
import order.domain.ReturnLine;
import order.domain.ReturnOrder;

/**
 * P4-F1-S3 returns: creation adjusts inventory (stock goes back on the
 * shelf via the reservation receipts' inventory items) and emits the credit
 * memo event `order.creditmemo.issued.v1`.
 */
public class ReturnService {

    public static final String RETURN_CREATED = "order.return.created.v1";
    public static final String CREDIT_MEMO_ISSUED = "order.creditmemo.issued.v1";

    private final ReturnStore returns;
    private final OrderStore orders;
    private final SagaStore sagas;
    private final InventoryGateway inventory;
    private final OutboxWriter outbox;
    private final OrderSerializer serializer;

    public ReturnService(ReturnStore returns, OrderStore orders, SagaStore sagas,
            InventoryGateway inventory, OutboxWriter outbox, OrderSerializer serializer) {
        this.returns = returns;
        this.orders = orders;
        this.sagas = sagas;
        this.inventory = inventory;
        this.outbox = outbox;
        this.serializer = serializer;
    }

    public Optional<ReturnOrder> get(String returnId) {
        return returns.find(returnId);
    }

    /**
     * @throws IllegalStateException the order is not COMPLETED yet.
     */
    public ReturnOrder create(String orderId, List<ReturnLine> lines) {
        Order order = orders.find(orderId)
            .orElseThrow(() -> new NoSuchElementException("no order " + orderId));
        if (order.status() != OrderStatus.COMPLETED) {
            throw new IllegalStateException(
                "order " + orderId + " is " + order.status() + ", returns need COMPLETED");
        }
        List<InventoryGateway.ReservationReceipt> receipts = sagas.find(orderId)
            .map(SagaStore.SagaState::reservations).orElse(List.of());
        for (ReturnLine line : lines) {
            String inventoryItemId = receipts.stream()
                .filter(r -> r.productId().equals(line.productId())
                    && r.facilityId().equals(line.facilityId()))
                .map(InventoryGateway.ReservationReceipt::inventoryItemId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "no reservation receipt for " + line.productId() + " at "
                        + line.facilityId() + " — cannot adjust inventory"));
            inventory.receive(inventoryItemId, line.quantity());
        }
        ReturnOrder returnOrder = new ReturnOrder("RET-" + UUID.randomUUID(), orderId, lines);
        returnOrder.complete();
        returns.save(returnOrder);
        outbox.append(RETURN_CREATED, returnOrder.returnId(), serializer.serializeReturn(returnOrder));
        outbox.append(CREDIT_MEMO_ISSUED, returnOrder.returnId(),
            serializer.serializeReturn(returnOrder));
        return returnOrder;
    }
}
