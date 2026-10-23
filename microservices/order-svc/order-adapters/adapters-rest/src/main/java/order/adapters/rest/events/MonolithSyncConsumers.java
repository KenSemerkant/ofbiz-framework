package order.adapters.rest.events;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import order.adapters.rest.persistence.JdbcMonolithOrderSyncWriter;
import order.adapters.rest.persistence.OrderJson;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Monolith interop (P4-GATE cutover, party/product template):
 * <ul>
 * <li>reverse-sync: order.order.* events → staging monolith ORDER_HEADER /
 *     ORDER_ITEM / ORDER_STATUS_HISTORY (new-service wins) so the rollback
 *     path keeps the monolith tables current</li>
 * <li>CDC projection: ofbiz.ORDER_HEADER/ORDER_ITEM → local projection rows
 *     (rollback drill truth source when the route flips back)</li>
 * </ul>
 */
public final class MonolithSyncConsumers {

    @Component
    @ConditionalOnProperty(name = "order.monolith.sync.enabled", havingValue = "true")
    public static class ReverseSync {

        private final JdbcMonolithOrderSyncWriter writer;
        private final ObjectMapper json = new ObjectMapper();

        public ReverseSync(JdbcMonolithOrderSyncWriter writer) {
            this.writer = writer;
        }

        @KafkaListener(id = "order-reverse-sync",
                topics = {"order.order.placed.v1", "order.order.approved.v1",
                        "order.order.completed.v1", "order.order.cancelled.v1"},
                properties = {"auto.offset.reset=latest"})
        public void onOrderEvent(String payload) {
            JsonNode event = json.readTree(payload);
            String orderId = event.path("orderId").asText();
            String status = event.has("status") ? event.path("status").asText("CREATED")
                : "CREATED";
            writer.upsertHeader(orderId, event.path("customerId").asText(), status,
                event.path("currency").asText("USD"), event.path("grandTotal").asText("0"));
            if (event.has("lines")) {
                writer.upsertItems(orderId, json.convertValue(event.path("lines"),
                    json.getTypeFactory().constructCollectionType(java.util.ArrayList.class,
                        OrderJson.LineDto.class)));
            }
            writer.appendHistory(orderId, status, event.path("note").asText(null));
        }
    }

    @Component
    @ConditionalOnProperty(name = "order.cdc.enabled", havingValue = "true")
    public static class CdcProjection {

        private final JdbcTemplate db;

        public CdcProjection(JdbcTemplate db) {
            this.db = db; // unqualified: the @Primary own-DB template wins when sync is on
        }

        @KafkaListener(id = "order-monolith-projection",
                topics = {"ofbiz.ORDER_HEADER", "ofbiz.ORDER_ITEM"},
                properties = {"auto.offset.reset=earliest"})
        public void onCdcEvent(ConsumerRecord<String, String> record) {
            String eventId = "%s:%d:%d".formatted(record.topic(), record.partition(),
                record.offset());
            db.update("""
                insert into monolith_order_projection (event_id, topic, payload, applied_at)
                values (?, ?, ?, now())
                on conflict (event_id) do nothing
                """, eventId, record.topic(), record.value());
        }
    }

    private MonolithSyncConsumers() {
    }
}
