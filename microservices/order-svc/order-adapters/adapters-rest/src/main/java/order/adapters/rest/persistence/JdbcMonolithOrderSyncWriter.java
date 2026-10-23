package order.adapters.rest.persistence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Reverse-sync writes into the staging monolith tables (new-service wins,
 * ADR-012 party-svc conflict policy): ORDER_HEADER upsert per status event,
 * ORDER_ITEM rows + ORDER_STATUS_HISTORY rows from the placed payload.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "order.monolith.sync.enabled", havingValue = "true")
public class JdbcMonolithOrderSyncWriter {

    private final JdbcTemplate monolith;

    public JdbcMonolithOrderSyncWriter(@Qualifier("monolithJdbc") JdbcTemplate monolith) {
        this.monolith = monolith;
    }

    public void upsertHeader(String orderId, String customerId, String status,
            String currency, String grandTotal) {
        monolith.update("""
            insert into ORDER_HEADER (ORDER_ID, CUSTOMER_ID, STATUS_ID, CURRENCY_UOM, GRAND_TOTAL)
            values (?, ?, ?, ?, ?)
            on conflict (ORDER_ID) do update set
                STATUS_ID = excluded.STATUS_ID,
                GRAND_TOTAL = excluded.GRAND_TOTAL
            """, orderId, customerId, status, currency, new java.math.BigDecimal(grandTotal));
    }

    public void upsertItems(String orderId, List<OrderJson.LineDto> lines) {
        for (OrderJson.LineDto line : lines) {
            monolith.update("""
                insert into ORDER_ITEM (ORDER_ID, ORDER_ITEM_SEQ_ID, PRODUCT_ID, QUANTITY,
                    UNIT_PRICE, UNIT_LIST_PRICE)
                values (?, ?, ?, ?, ?, ?)
                on conflict (ORDER_ID, ORDER_ITEM_SEQ_ID) do update set
                    QUANTITY = excluded.QUANTITY,
                    UNIT_PRICE = excluded.UNIT_PRICE
                """, orderId, line.orderItemSeqId(), line.productId(), line.quantity(),
                line.unitPrice(), line.unitPrice());
        }
    }

    public void appendHistory(String orderId, String status, String note) {
        monolith.update("""
            insert into ORDER_STATUS_HISTORY (ORDER_ID, STATUS_ID, NOTE, CHANGED_AT)
            values (?, ?, ?, now())
            """, orderId, status, note);
    }
}
