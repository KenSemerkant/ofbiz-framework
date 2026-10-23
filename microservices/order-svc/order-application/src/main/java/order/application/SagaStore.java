package order.application;

import java.util.List;
import java.util.Optional;

/**
 * P4-F2-S1 saga state: persisted per order so the orchestrator is resumable
 * after a restart (AC) and idempotent under Kafka redelivery — every marker
 * below is a guarded transition; a second call is a no-op.
 */
public interface SagaStore {

    enum Step { PLACED, RESERVING, RESERVED, INVOICED, DONE, COMPENSATED }

    record SagaState(String orderId, Step step, List<InventoryGateway.ReservationReceipt> reservations) {
    }

    void create(String orderId);

    Optional<SagaState> find(String orderId);

    /** Guarded PLACED→RESERVING: false when the saga is not at PLACED. */
    boolean markReserving(String orderId);

    /** RESERVING→RESERVED together with the reserve receipts. */
    void storeReservations(String orderId, List<InventoryGateway.ReservationReceipt> receipts);

    /** Guarded RESERVED→INVOICED: false when invoicing already happened. */
    boolean markInvoiced(String orderId);

    void markDone(String orderId);

    void markCompensated(String orderId);

    /** Orders whose saga never reached DONE/COMPENSATED (restart scan). */
    List<String> findIncompleteOrderIds();
}
