package inventory.application;

import java.util.Optional;
import inventory.domain.Facility;

/**
 * P3-F2-S1 use case: facility CRUD. First write emits
 * `inventory.facility.created.v1`, later writes `inventory.facility.updated.v1`,
 * removal `inventory.facility.deleted.v1`.
 */
public class FacilityService {

    public static final String FACILITY_CREATED = "inventory.facility.created.v1";
    public static final String FACILITY_UPDATED = "inventory.facility.updated.v1";
    public static final String FACILITY_DELETED = "inventory.facility.deleted.v1";

    private final FacilityStore facilities;
    private final OutboxWriter outbox;
    private final InventorySerializer serializer;

    public FacilityService(FacilityStore facilities, OutboxWriter outbox,
            InventorySerializer serializer) {
        this.facilities = facilities;
        this.outbox = outbox;
        this.serializer = serializer;
    }

    /** P9-F2: facility listing for the SPA catalog area. */
    public java.util.List<Facility> all() {
        return facilities.all().stream()
            .sorted(java.util.Comparator.comparing(Facility::facilityId)).toList();
    }

    public Optional<Facility> get(String facilityId) {
        return facilities.find(facilityId);
    }

    public void upsert(Facility facility) {
        Optional<Facility> existing = facilities.find(facility.facilityId());
        facilities.save(facility);
        outbox.append(existing.isEmpty() ? FACILITY_CREATED : FACILITY_UPDATED,
            facility.facilityId(), serializer.serializeFacility(facility));
    }

    /** @return false when the facility does not exist (no delete, no event). */
    public boolean delete(String facilityId) {
        if (facilities.find(facilityId).isEmpty()) {
            return false;
        }
        facilities.delete(facilityId);
        outbox.append(FACILITY_DELETED, facilityId, serializer.serializeFacilityDeleted(facilityId));
        return true;
    }
}
