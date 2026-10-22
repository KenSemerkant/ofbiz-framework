package product.adapters.rest.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * P3-GATE: parses the Debezium envelope and mirrors the change into the
 * monolith product projection tables (one table per mirrored monolith
 * table). A PRODUCT delete cascades memberships/prices/assocs; rows on the
 * other topics are keyed upserts/deletes. The event id lands in
 * processed_events in the SAME transaction as the projection write.
 */
@Repository
public class JdbcMonolithProductProjectionSink
        implements ofbiz.projection.IdempotentProjector.Sink {

    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final ObjectMapper json = new ObjectMapper();

    public JdbcMonolithProductProjectionSink(JdbcTemplate db,
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
                case "PRODUCT" -> applyProduct(op, row);
                case "PRODUCT_CATEGORY" -> applyCategory(op, row);
                case "PRODUCT_CATEGORY_MEMBER" -> applyMember(op, row);
                case "PRODUCT_PRICE" -> applyPrice(op, row);
                case "PRODUCT_ASSOC" -> applyAssoc(op, row);
                default -> { /* not part of the product slice — nothing to do */ }
            }
            db.update("INSERT INTO processed_events (event_id) VALUES (?) "
                + "ON CONFLICT DO NOTHING", eventId);
        });
    }

    private void applyProduct(String op, JsonNode row) {
        String productId = row.path("PRODUCT_ID").asText();
        if (op.equals("d")) {
            deleteProductSlice(productId);
            return;
        }
        db.update("""
            INSERT INTO monolith_products
                (product_id, product_type_id, internal_name, product_name)
            VALUES (?,?,?,?)
            ON CONFLICT (product_id) DO UPDATE
              SET product_type_id = EXCLUDED.product_type_id,
                  internal_name  = EXCLUDED.internal_name,
                  product_name   = EXCLUDED.product_name
            """, productId, row.path("PRODUCT_TYPE_ID").asText(null),
            row.path("INTERNAL_NAME").asText(null), row.path("PRODUCT_NAME").asText(null));
    }

    private void deleteProductSlice(String productId) {
        db.update("DELETE FROM monolith_product_members WHERE product_id = ?", productId);
        db.update("DELETE FROM monolith_product_prices WHERE product_id = ?", productId);
        db.update("DELETE FROM monolith_product_assocs WHERE product_id = ?", productId);
        db.update("DELETE FROM monolith_products WHERE product_id = ?", productId);
    }

    private void applyCategory(String op, JsonNode row) {
        String categoryId = row.path("PRODUCT_CATEGORY_ID").asText();
        if (op.equals("d")) {
            db.update("DELETE FROM monolith_product_categories WHERE product_category_id = ?",
                categoryId);
            return;
        }
        db.update("""
            INSERT INTO monolith_product_categories (product_category_id, category_name)
            VALUES (?,?)
            ON CONFLICT (product_category_id) DO UPDATE
              SET category_name = EXCLUDED.category_name
            """, categoryId, row.path("CATEGORY_NAME").asText(null));
    }

    private void applyMember(String op, JsonNode row) {
        String productId = row.path("PRODUCT_ID").asText();
        String categoryId = row.path("PRODUCT_CATEGORY_ID").asText();
        if (op.equals("d")) {
            db.update("""
                DELETE FROM monolith_product_members
                WHERE product_id = ? AND product_category_id = ?
                """, productId, categoryId);
            return;
        }
        db.update("""
            INSERT INTO monolith_product_members (product_id, product_category_id)
            VALUES (?,?)
            ON CONFLICT DO NOTHING
            """, productId, categoryId);
    }

    private void applyPrice(String op, JsonNode row) {
        String productId = row.path("PRODUCT_ID").asText();
        String typeId = row.path("PRODUCT_PRICE_TYPE_ID").asText();
        String currency = row.path("CURRENCY_UOM_ID").asText();
        String fromDate = row.path("FROM_DATE").asText();
        if (op.equals("d")) {
            db.update("""
                DELETE FROM monolith_product_prices
                WHERE product_id = ? AND product_price_type_id = ?
                  AND currency_uom_id = ? AND from_date = ?
                """, productId, typeId, currency, fromDate);
            return;
        }
        db.update("""
            INSERT INTO monolith_product_prices
                (product_id, product_price_type_id, currency_uom_id, from_date, price)
            VALUES (?,?,?,?,?)
            ON CONFLICT (product_id, product_price_type_id, currency_uom_id, from_date)
              DO UPDATE SET price = EXCLUDED.price
            """, productId, typeId, currency, fromDate, row.path("PRICE").asText());
    }

    private void applyAssoc(String op, JsonNode row) {
        String productId = row.path("PRODUCT_ID").asText();
        String productIdTo = row.path("PRODUCT_ID_TO").asText();
        String typeId = row.path("PRODUCT_ASSOC_TYPE_ID").asText();
        String fromDate = row.path("FROM_DATE").asText();
        if (op.equals("d")) {
            db.update("""
                DELETE FROM monolith_product_assocs
                WHERE product_id = ? AND product_id_to = ?
                  AND product_assoc_type_id = ? AND from_date = ?
                """, productId, productIdTo, typeId, fromDate);
            return;
        }
        db.update("""
            INSERT INTO monolith_product_assocs
                (product_id, product_id_to, product_assoc_type_id, from_date)
            VALUES (?,?,?,?)
            ON CONFLICT DO NOTHING
            """, productId, productIdTo, typeId, fromDate);
    }
}
