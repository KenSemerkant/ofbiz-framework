package inventory.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import inventory.adapters.rest.events.OutboxRelay;
import inventory.adapters.rest.persistence.FacilityEntity;
import inventory.adapters.rest.persistence.InventoryItemEntity;
import inventory.adapters.rest.persistence.JacksonInventorySerializer;
import inventory.adapters.rest.persistence.OutboxEntryEntity;
import inventory.adapters.rest.persistence.OutboxRepository;
import inventory.domain.Facility;
import inventory.domain.InventoryItem;
import inventory.domain.Reservation;
import inventory.adapters.rest.persistence.ReservationEntity;

/**
 * Unit coverage for adapter paths the container tests don't reach: the outbox
 * relay (publish-then-mark), the event payload shape and the JPA entity
 * accessors.
 */
class InventoryAdaptersUnitTest {

    @Test
    void outboxRelayPublishesThenMarks() {
        boolean[] drained = {false};
        OutboxRepository outbox = (OutboxRepository) Proxy.newProxyInstance(
            OutboxRepository.class.getClassLoader(), new Class<?>[] {OutboxRepository.class},
            (p, method, args) -> switch (method.getName()) {
                case "findUnpublished" -> drained[0] ? List.of()
                    : List.of(new OutboxEntryEntity("inventory.stock.received.v1", "II1", "{}"));
                case "findById" -> {
                    // the relay marks the fetched row published inside the tx
                    OutboxEntryEntity e = new OutboxEntryEntity("t", "k", "{}");
                    e.markPublished(Instant.now());
                    drained[0] = true;
                    yield Optional.of(e);
                }
                default -> null;
            });
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafka = new KafkaTemplate<String, String>(
                new ProducerFactory<String, String>() {
                    @Override
                    public org.apache.kafka.clients.producer.Producer<String, String>
                        createProducer() {
                        throw new IllegalStateException("send() is overridden");
                    }

                    @Override
                    public org.apache.kafka.clients.producer.Producer<String, String>
                        createProducer(String txIdPrefix) {
                        throw new IllegalStateException("send() is overridden");
                    }
                }) {
            @Override
            public CompletableFuture<SendResult<String, String>> send(String topic, String key,
                    String value) {
                return CompletableFuture.completedFuture(null);
            }
        };
        TransactionTemplate transactions = new TransactionTemplate(noopTransactionManager());
        new OutboxRelay(outbox, kafka, transactions).publishPending();
        assertTrue(drained[0], "relay should have drained the pending row");
    }

    private static PlatformTransactionManager noopTransactionManager() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
    }

    @Test
    void serializerPayloadShape() {
        JacksonInventorySerializer serializer = new JacksonInventorySerializer();
        InventoryItem item = new InventoryItem("II1", "WIDGET", "WH1", "SN-1",
            new BigDecimal("9.99"));
        item.receive(new BigDecimal("10"));

        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        var stock = mapper.readTree(serializer.serializeStockChange(item, new BigDecimal("10")));
        assertEquals("II1", stock.path("inventoryItemId").asText());
        assertEquals("WIDGET", stock.path("productId").asText());
        assertEquals("WH1", stock.path("facilityId").asText());
        assertEquals(0, stock.path("quantityOnHandTotal").decimalValue()
            .compareTo(new BigDecimal("10")));
        assertEquals(0, stock.path("availableToPromiseTotal").decimalValue()
            .compareTo(new BigDecimal("10")));
        assertEquals(0, stock.path("quantity").decimalValue().compareTo(new BigDecimal("10")));
        assertEquals("SN-1", stock.path("serialNumber").asText());

        var facility = mapper.readTree(serializer.serializeFacility(
            new Facility("WH1", "Main", "WAREHOUSE")));
        assertEquals("WH1", facility.path("facilityId").asText());
        assertEquals("Main", facility.path("facilityName").asText());
        assertEquals("WAREHOUSE", facility.path("facilityTypeId").asText());

        var transfer = mapper.readTree(serializer.serializeTransfer(item,
            new InventoryItem("II2", "WIDGET", "WH2", null, null), new BigDecimal("4")));
        assertEquals("II1", transfer.path("from").path("inventoryItemId").asText());
        assertEquals("II2", transfer.path("to").path("inventoryItemId").asText());
        assertEquals(0, transfer.path("quantity").decimalValue().compareTo(new BigDecimal("4")));
    }

    @Test
    void serializerPayloadsEscapeHostileFields() {
        JacksonInventorySerializer serializer = new JacksonInventorySerializer();
        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        InventoryItem hostile = new InventoryItem("II\"Q", "P\\X", "F\"K", "S\"N", null);

        assertEquals("II\"Q", mapper.readTree(serializer.serializeItem(hostile))
            .path("inventoryItemId").asText());
        assertEquals("P\\X", mapper.readTree(serializer.serializeItem(hostile))
            .path("productId").asText());
        assertEquals("F\"K", mapper.readTree(
                serializer.serializeFacilityDeleted("F\"K")).path("facilityId").asText());
        assertEquals("Sharp \"Name\" \\ Best",
            mapper.readTree(serializer.serializeFacility(new Facility("WH1",
                "Sharp \"Name\" \\ Best", null))).path("facilityName").asText());
    }

    @Test
    void reservationSerializerPayloadShape() {
        JacksonInventorySerializer serializer = new JacksonInventorySerializer();
        InventoryItem item = new InventoryItem("II1", "WIDGET", "WH1", null, null);
        item.receive(new BigDecimal("10"));
        Reservation reservation = new Reservation("RES-1", "ORD-1", "00001", "WIDGET", "WH1",
            "II1", new BigDecimal("4"));
        item.reserve(new BigDecimal("4"));

        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        var payload = mapper.readTree(serializer.serializeReservation(reservation, item));
        assertEquals("RES-1", payload.path("reservationId").asText());
        assertEquals("ORD-1", payload.path("orderId").asText());
        assertEquals("00001", payload.path("orderItemSeqId").asText());
        assertEquals("II1", payload.path("inventoryItemId").asText());
        assertEquals(0, payload.path("quantity").decimalValue()
            .compareTo(new BigDecimal("4")));
        assertEquals("CREATED", payload.path("status").asText());
        assertEquals("II1", payload.path("item").path("inventoryItemId").asText());
        assertEquals(0, payload.path("item").path("availableToPromiseTotal").decimalValue()
            .compareTo(new BigDecimal("6")));
    }

    @Test
    void reservationEntityAccessorsAndRoundtrip() {
        ReservationEntity entity = new ReservationEntity("RES-1");
        entity.applyFrom(new Reservation("RES-1", "ORD-1", "00001", "WIDGET", "WH1", "II1",
            new BigDecimal("4")));
        assertEquals("RES-1", entity.getReservationId());
        assertEquals("ORD-1", entity.getOrderId());
        assertEquals("00001", entity.getOrderItemSeqId());
        assertEquals("WIDGET", entity.getProductId());
        assertEquals("WH1", entity.getFacilityId());
        assertEquals("II1", entity.getInventoryItemId());
        assertEquals(0, entity.getQuantity().compareTo(new BigDecimal("4")));
        assertEquals("CREATED", entity.getStatus());
        assertTrue(entity.getUpdatedAt() != null);

        Reservation domain = entity.toDomain();
        assertEquals("RES-1", domain.reservationId());
        assertEquals(Reservation.Status.CREATED, domain.status());
        assertEquals(0, domain.quantity().compareTo(new BigDecimal("4")));

        assertTrue(new ReservationEntity().getReservationId() == null);
    }

    @Test
    void entityAccessorsAndApplyFrom() {
        InventoryItemEntity entity = new InventoryItemEntity("II1");
        assertEquals("II1", entity.getInventoryItemId());
        assertEquals(0, entity.getVersion());

        entity.applyFrom(new InventoryItem("II1", "WIDGET", "WH1", "SN-1",
            new BigDecimal("9.99"), new BigDecimal("10"), new BigDecimal("8")));
        assertEquals("WIDGET", entity.getProductId());
        assertEquals("WH1", entity.getFacilityId());
        assertEquals("SN-1", entity.getSerialNumber());
        assertEquals(0, entity.getUnitCost().compareTo(new BigDecimal("9.99")));
        assertEquals(0, entity.getQuantityOnHandTotal().compareTo(new BigDecimal("10")));
        assertEquals(0, entity.getAvailableToPromiseTotal().compareTo(new BigDecimal("8")));
        assertTrue(entity.getUpdatedAt() != null);

        InventoryItem domain = entity.toDomain();
        assertEquals("II1", domain.inventoryItemId());
        assertEquals(0, domain.quantityOnHandTotal().compareTo(new BigDecimal("10")));
        assertEquals(0, domain.availableToPromiseTotal().compareTo(new BigDecimal("8")));

        assertTrue(new InventoryItemEntity().getInventoryItemId() == null);

        FacilityEntity facility = new FacilityEntity("WH1", "Main", "WAREHOUSE");
        assertEquals("WH1", facility.getFacilityId());
        assertEquals("Main", facility.getFacilityName());
        assertEquals("WAREHOUSE", facility.getFacilityTypeId());
        assertTrue(facility.getUpdatedAt() != null);
        assertTrue(new FacilityEntity().getFacilityId() == null);

        OutboxEntryEntity entry = new OutboxEntryEntity("t", "k", "{}");
        assertEquals("t", entry.getEventType());
        assertEquals("k", entry.getAggregateKey());
        assertEquals("{}", entry.getPayload());
        assertTrue(entry.getOccurredAt() != null);
        assertTrue(entry.getPublishedAt() == null);
        entry.markPublished(Instant.now());
        assertTrue(entry.getPublishedAt() != null);
        assertTrue(entry.getId() == null);
        assertTrue(new OutboxEntryEntity().getEventType() == null);
    }
}
