package inventory.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import inventory.domain.Facility;

/** AC (P3-F2-S1): facility CRUD emits created/updated/deleted events. */
class FacilityServiceTest {

    private final Map<String, Facility> storeData = new HashMap<>();
    private final List<String> outboxEvents = new ArrayList<>();

    private final FacilityService service = new FacilityService(
        new InMemoryFacilityStore(), (type, key, payload) -> outboxEvents.add(type),
        new StubSerializer());

    @Test
    void upsertEmitsCreatedThenUpdated() {
        service.upsert(new Facility("WH1", "Main", "WAREHOUSE"));
        assertEquals(List.of(FacilityService.FACILITY_CREATED), outboxEvents);
        service.upsert(new Facility("WH1", "Renamed", "WAREHOUSE"));
        assertEquals(List.of(FacilityService.FACILITY_CREATED, FacilityService.FACILITY_UPDATED),
            outboxEvents);
        assertEquals("Renamed", service.get("WH1").orElseThrow().facilityName());
    }

    /** P9-F2: facility listing for the SPA catalog area. */
    @Test
    void allListsCreatedFacilitiesSortedById() {
        service.upsert(new Facility("WH2", "Second", "WAREHOUSE"));
        service.upsert(new Facility("WH1", "Main", "WAREHOUSE"));
        assertEquals(List.of("WH1", "WH2"),
            service.all().stream().map(Facility::facilityId).toList());
    }

    @Test
    void deleteEmitsDeletedOnlyWhenPresent() {
        assertFalse(service.delete("nope"));
        assertTrue(outboxEvents.isEmpty());

        service.upsert(new Facility("WH1", "Main", null));
        assertTrue(service.delete("WH1"));
        assertTrue(service.get("WH1").isEmpty());
        assertEquals(FacilityService.FACILITY_DELETED, outboxEvents.get(outboxEvents.size() - 1));
    }

    private class InMemoryFacilityStore implements FacilityStore {

        @Override
        public Optional<Facility> find(String facilityId) {
            return Optional.ofNullable(storeData.get(facilityId));
        }

        @Override
        public java.util.List<Facility> all() {
            return storeData.values().stream().toList();
        }

        @Override
        public void save(Facility facility) {
            storeData.put(facility.facilityId(), facility);
        }

        @Override
        public boolean delete(String facilityId) {
            return storeData.remove(facilityId) != null;
        }
    }

    private static final class StubSerializer implements InventorySerializer {

        @Override
        public String serializeItem(inventory.domain.InventoryItem item) {
            return "{}";
        }

        @Override
        public String serializeFacility(Facility facility) {
            return "{}";
        }

        @Override
        public String serializeFacilityDeleted(String facilityId) {
            return "{}";
        }

        @Override
        public String serializeStockChange(inventory.domain.InventoryItem item,
                java.math.BigDecimal quantity) {
            return "{}";
        }

        @Override
        public String serializeShipment(inventory.domain.Shipment shipment) {
            return "{}";
        }

        @Override
        public String serializeReservation(inventory.domain.Reservation reservation,
                inventory.domain.InventoryItem item) {
            return "{}";
        }

        @Override
        public String serializeTransfer(inventory.domain.InventoryItem from,
                inventory.domain.InventoryItem to, java.math.BigDecimal quantity) {
            return "{}";
        }
    }
}
