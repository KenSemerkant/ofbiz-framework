package inventory.adapters.rest.api;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import inventory.application.FacilityService;
import inventory.domain.Facility;

/**
 * Adapter-layer transaction boundary for facility writes (ADR-009): the row
 * and its outbox event commit together. A separate bean (not the controller
 * method) so a duplicate-id PK race surfaces from the proxy call INSIDE the
 * controller's catch — a commit-time exception can't be caught by code that
 * runs before the commit (quality review).
 */
@Component
public class FacilityOperations {

    private final FacilityService facilityService;

    public FacilityOperations(FacilityService facilityService) {
        this.facilityService = facilityService;
    }

    @Transactional
    public void upsert(Facility facility) {
        facilityService.upsert(facility);
    }

    @Transactional
    public boolean delete(String facilityId) {
        return facilityService.delete(facilityId);
    }
}
