package inventory.adapters.rest.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * P3-GATE: parses the Debezium envelope and mirrors the change into the
 * monolith shipment projection tables. A SHIPMENT delete cascades its
 * items; SHIPMENT_ITEM rows are keyed upserts/deletes. The event id lands
 * in processed_events in the SAME transaction as the projection write.
 */
@Repository
public class JdbcMonolithShipmentProjectionSink
        implements ofbiz.projection.IdempotentProjector.Sink {

    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final ObjectMapper json = new ObjectMapper();

    public JdbcMonolithShipmentProjectionSink(JdbcTemplate db,
            TransactionTemplate transactions) {
        this.db = db;
        this.transactions = transactions;
    }

    @Override
    public boolean alreadyProcessed(String eventId) {
        Boolean seen = db.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM processed_events WHERE event_id = ?)",
            Boolean.class, eventId);
        return Boolean.TRUE.equals(seen);
    }

    @Override
    public void apply(String eventId, String envelopeJson) {
        transactions.executeWithoutResult(tx -> {
            JsonNode root = json.readTree(envelopeJson);
            // JsonConverter with schemas.enable wraps the change event in a
            // "payload" object — unwrap when present (KNOWN ISSUE P2-F2-S1).
            if (root.isObject() && root.has("payload") && root.path("payload").isObject()) {
                root = root.path("payload");
            }
            String op = root.path("op").asText();
            JsonNode row = op.equals("d") ? root.path("before") : root.path("after");
            switch (root.path("source").path("table").asText()) {
                case "SHIPMENT" -> applyShipment(op, row);
                case "SHIPMENT_ITEM" -> applyItem(op, row);
                default -> { /* not part of the shipment slice — nothing to do */ }
            }
            db.update("INSERT INTO processed_events (event_id) VALUES (?) "
                + "ON CONFLICT DO NOTHING", eventId);
        });
    }

    private void applyShipment(String op, JsonNode row) {
        String shipmentId = row.path("SHIPMENT_ID").asText();
        if (op.equals("d")) {
            db.update("DELETE FROM monolith_shipment_items WHERE shipment_id = ?",
                shipmentId);
            db.update("DELETE FROM monolith_shipments WHERE shipment_id = ?", shipmentId);
            return;
        }
        db.update("""
            INSERT INTO monolith_shipments (shipment_id, status_id)
            VALUES (?,?)
            ON CONFLICT (shipment_id) DO UPDATE
              SET status_id = EXCLUDED.status_id
            """, shipmentId, row.path("STATUS_ID").asText(null));
    }

    private void applyItem(String op, JsonNode row) {
        String shipmentId = row.path("SHIPMENT_ID").asText();
        String seqId = row.path("SHIPMENT_ITEM_SEQ_ID").asText();
        if (op.equals("d")) {
            db.update("""
                DELETE FROM monolith_shipment_items
                WHERE shipment_id = ? AND shipment_item_seq_id = ?
                """, shipmentId, seqId);
            return;
        }
        db.update("""
            INSERT INTO monolith_shipment_items
                (shipment_id, shipment_item_seq_id, product_id, quantity)
            VALUES (?,?,?,?)
            ON CONFLICT (shipment_id, shipment_item_seq_id) DO UPDATE
              SET product_id = EXCLUDED.product_id,
                  quantity   = EXCLUDED.quantity
            """, shipmentId, seqId, row.path("PRODUCT_ID").asText(null),
            row.path("QUANTITY").asText(null));
    }
}
