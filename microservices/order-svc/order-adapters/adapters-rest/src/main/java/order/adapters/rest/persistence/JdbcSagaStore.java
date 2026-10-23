package order.adapters.rest.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import order.application.InventoryGateway;
import order.application.SagaStore;

/**
 * Saga state as guarded SQL transitions (P4-F2-S1): every marker update
 * carries a WHERE step = <expected> guard, so concurrent redeliveries and
 * the restart rescan cannot double-advance a saga.
 */
@Repository
public class JdbcSagaStore implements SagaStore {

    private final JdbcTemplate db;

    public JdbcSagaStore(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    public void create(String orderId) {
        db.update(
            "insert into saga_state (order_id, step, reservations) values (?, 'PLACED', '[]'::jsonb)",
            orderId);
    }

    @Override
    public Optional<SagaState> find(String orderId) {
        List<SagaState> found = db.query(
            "select order_id, step, reservations from saga_state where order_id = ?",
            (rs, i) -> new SagaState(rs.getString(1), Step.valueOf(rs.getString(2)),
                OrderJson.deserializeReceipts(rs.getString(3))),
            orderId);
        return found.stream().findFirst();
    }

    @Override
    public boolean markReserving(String orderId) {
        return db.update(
            "update saga_state set step = 'RESERVING' where order_id = ? and step = 'PLACED'",
            orderId) == 1;
    }

    @Override
    public void storeReservations(String orderId,
            List<InventoryGateway.ReservationReceipt> receipts) {
        db.update("update saga_state set step = 'RESERVED', reservations = ?::jsonb "
            + "where order_id = ? and step = 'RESERVING'",
            OrderJson.serializeReceipts(receipts), orderId);
    }

    @Override
    public boolean markInvoiced(String orderId) {
        return db.update(
            "update saga_state set step = 'INVOICED' where order_id = ? and step = 'RESERVED'",
            orderId) == 1;
    }

    @Override
    public void markDone(String orderId) {
        db.update("update saga_state set step = 'DONE' where order_id = ?", orderId);
    }

    @Override
    public void markCompensated(String orderId) {
        db.update("update saga_state set step = 'COMPENSATED' where order_id = ?", orderId);
    }

    @Override
    public List<String> findIncompleteOrderIds() {
        return db.queryForList("select order_id from saga_state where step in "
            + "('PLACED','RESERVING','RESERVED','INVOICED') order by order_id", String.class);
    }
}
