package inventory.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import inventory.domain.InventoryItem;

/**
 * AC (P3-F2-S1): receive/adjust/transfer flows mutate ATP/QOH and emit the
 * matching `inventory.stock.*` event; missing items and insufficient stock are
 * surfaced as distinct failure types.
 */
class InventoryServiceTest {

    private static final BigDecimal TEN = new BigDecimal("10");

    private final Map<String, InventoryItem> storeData = new HashMap<>();
    private final List<String> outboxEvents = new ArrayList<>();

    private final InventoryService service = new InventoryService(new InMemoryItemStore(),
        (type, key, payload) -> outboxEvents.add(type), new StubSerializer());

    @Test
    void createEmitsItemCreatedAndRejectsDuplicates() {
        service.create(new InventoryItem("II1", "WIDGET", "WH1", null, null));
        assertEquals(List.of(InventoryService.ITEM_CREATED), outboxEvents);
        assertThrows(IllegalStateException.class,
            () -> service.create(new InventoryItem("II1", "WIDGET", "WH1", null, null)));
    }

    @Test
    void receiveRaisesTotalsAndEmitsReceived() {
        service.create(new InventoryItem("II1", "WIDGET", "WH1", null, null));
        InventoryItem item = service.receive("II1", TEN);
        assertEquals(0, item.quantityOnHandTotal().compareTo(TEN));
        assertEquals(0, item.availableToPromiseTotal().compareTo(TEN));
        assertEquals(InventoryService.STOCK_RECEIVED, outboxEvents.get(outboxEvents.size() - 1));
    }

    @Test
    void adjustEmitsAdjustedAndGuardsTheFloor() {
        service.create(new InventoryItem("II1", "WIDGET", "WH1", null, null));
        service.receive("II1", TEN);
        service.adjust("II1", new BigDecimal("-2"));
        assertEquals(InventoryService.STOCK_ADJUSTED, outboxEvents.get(outboxEvents.size() - 1));
        assertEquals(0, service.get("II1").orElseThrow().quantityOnHandTotal()
            .compareTo(new BigDecimal("8")));
        assertThrows(IllegalStateException.class,
            () -> service.adjust("II1", new BigDecimal("-9")));
    }

    @Test
    void transferMovesBothItemsAndEmitsTransferred() {
        service.create(new InventoryItem("II1", "WIDGET", "WH1", null, null));
        service.create(new InventoryItem("II2", "WIDGET", "WH2", null, null));
        service.receive("II1", TEN);
        service.transfer("II1", "II2", new BigDecimal("3"));
        assertEquals(InventoryService.STOCK_TRANSFERRED,
            outboxEvents.get(outboxEvents.size() - 1));
        assertEquals(0, service.get("II1").orElseThrow().quantityOnHandTotal()
            .compareTo(new BigDecimal("7")));
        assertEquals(0, service.get("II2").orElseThrow().quantityOnHandTotal()
            .compareTo(new BigDecimal("3")));
    }

    @Test
    void transferRejectsInsufficientAtpWithoutSideEffects() {
        service.create(new InventoryItem("II1", "WIDGET", "WH1", null, null));
        service.create(new InventoryItem("II2", "WIDGET", "WH2", null, null));
        service.receive("II1", TEN);
        assertThrows(IllegalStateException.class,
            () -> service.transfer("II1", "II2", new BigDecimal("11")));
        assertEquals(0, service.get("II2").orElseThrow().quantityOnHandTotal().signum());
        // no transferred event for the failed op
        assertTrue(outboxEvents.stream()
            .noneMatch(InventoryService.STOCK_TRANSFERRED::equals));
    }

    @Test
    void unknownItemIsNoSuchElement() {
        assertThrows(NoSuchElementException.class, () -> service.receive("nope", TEN));
        assertThrows(NoSuchElementException.class,
            () -> service.adjust("nope", BigDecimal.ONE));
        assertThrows(NoSuchElementException.class,
            () -> service.transfer("nope", "nope2", TEN));
        assertTrue(service.get("nope").isEmpty());
    }

    private class InMemoryItemStore implements InventoryItemStore {

        @Override
        public Optional<InventoryItem> find(String inventoryItemId) {
            return Optional.ofNullable(storeData.get(inventoryItemId));
        }

        @Override
        public List<InventoryItem> findByProductIdAndFacilityId(String productId,
                String facilityId) {
            return storeData.values().stream()
                .filter(i -> i.productId().equals(productId)
                    && i.facilityId().equals(facilityId))
                .toList();
        }

        @Override
        public void save(InventoryItem item) {
            storeData.put(item.inventoryItemId(), item);
        }
    }

    private static final class StubSerializer implements InventorySerializer {

        @Override
        public String serializeItem(InventoryItem item) {
            return "{}";
        }

        @Override
        public String serializeFacility(inventory.domain.Facility facility) {
            return "{}";
        }

        @Override
        public String serializeFacilityDeleted(String facilityId) {
            return "{}";
        }

        @Override
        public String serializeStockChange(InventoryItem item, BigDecimal quantity) {
            return "{}";
        }

        @Override
        public String serializeReservation(inventory.domain.Reservation reservation,
                inventory.domain.InventoryItem item) {
            return "{}";
        }

        @Override
        public String serializeTransfer(InventoryItem from, InventoryItem to,
                BigDecimal quantity) {
            return "{}";
        }
    }
}
