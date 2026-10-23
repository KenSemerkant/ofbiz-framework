package order.application;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import order.domain.Order;

/**
 * P4-F2-S1 ECA-derived saga steps on `order.order.placed.v1` (the monolith
 * ECA rules order-svc replaces): reserve stock, trigger the invoice,
 * compensate on cancel. Every step advances the persisted saga row through
 * guarded markers, so a Kafka redelivery is a no-op and
 * {@link #resumePending()} re-drives incomplete sagas after a restart.
 */
public class OrderEcaService {

    public static final String INVOICE_REQUESTED = "order.order.invoicerequested.v1";

    private final OrderStore orders;
    private final SagaStore sagas;
    private final InventoryGateway inventory;
    private final OutboxWriter outbox;
    private final OrderSerializer serializer;

    public OrderEcaService(OrderStore orders, SagaStore sagas, InventoryGateway inventory,
            OutboxWriter outbox, OrderSerializer serializer) {
        this.orders = orders;
        this.sagas = sagas;
        this.inventory = inventory;
        this.outbox = outbox;
        this.serializer = serializer;
    }

    /**
     * Fires the ECA steps for one placed order. Idempotent per step.
     *
     * @throws InventoryUnavailableException when stock cannot cover a line —
     *         caller compensates via {@link #compensate}
     */
    public void onPlaced(String orderId) {
        Order order = orders.find(orderId)
            .orElseThrow(() -> new NoSuchElementException("no order " + orderId));
        reserve(order);
        invoice(order);
        sagas.markDone(orderId);
    }

    /** P4-F2-S1 compensation: release every live reservation. */
    public void compensate(String orderId) {
        sagas.find(orderId).ifPresent(saga -> {
            for (InventoryGateway.ReservationReceipt receipt : saga.reservations()) {
                inventory.release(receipt.reservationId());
            }
            sagas.markCompensated(orderId);
        });
    }

    /** Restart recovery (resumable AC): re-drive incomplete sagas. */
    public void resumePending() {
        for (String orderId : sagas.findIncompleteOrderIds()) {
            try {
                onPlaced(orderId);
            } catch (InventoryUnavailableException e) {
                compensate(orderId);
            }
        }
    }

    private void reserve(Order order) {
        // RESERVING means a previous attempt died mid-reserve: receipts were
        // never stored, so re-reserve (worst case a duplicate reservation on
        // a crash between the inventory call and storeReservations — bounded,
        // // ponytail: reconciled by the P5 billing consistency checker if it matters)
        if (!sagas.markReserving(order.orderId())) {
            return; // already reserved or beyond — redelivery no-op
        }
        List<InventoryGateway.ReservationReceipt> receipts = new ArrayList<>();
        try {
            for (var line : order.lines()) {
                receipts.add(inventory.reserve(order.orderId(), line.orderItemSeqId(),
                    line.productId(), line.facilityId(), line.quantity()));
            }
        } catch (InventoryUnavailableException e) {
            receipts.forEach(r -> inventory.release(r.reservationId()));
            sagas.markCompensated(order.orderId());
            throw e;
        }
        sagas.storeReservations(order.orderId(), receipts);
    }

    private void invoice(Order order) {
        if (sagas.find(order.orderId())
            .map(s -> s.step() != SagaStore.Step.RESERVED)
            .orElse(true)) {
            return; // not at the invoicing point (or already past) — no-op
        }
        if (sagas.markInvoiced(order.orderId())) {
            outbox.append(INVOICE_REQUESTED, order.orderId(),
                serializer.serializeInvoiceRequest(order));
        }
    }
}
