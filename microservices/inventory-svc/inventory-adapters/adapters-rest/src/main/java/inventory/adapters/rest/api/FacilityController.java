package inventory.adapters.rest.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import inventory.application.FacilityService;
import inventory.domain.Facility;

/** North-south REST edge (ADR-010): only the gateway routes here. */
@RestController
@RequestMapping("/api/v1/facilities")
public class FacilityController {

    private final FacilityService facilityService;
    private final FacilityOperations operations;

    public FacilityController(FacilityService facilityService, FacilityOperations operations) {
        this.facilityService = facilityService;
        this.operations = operations;
    }

    public record UpsertFacilityRequest(String facilityId, String facilityName,
            String facilityTypeId) {
    }

    public record FacilityResponse(String facilityId, String facilityName,
            String facilityTypeId) {
    }

    /**
     * Adapter-layer transaction boundary (ADR-009): write + event commit
     * together. A duplicate-id PK race from the operations bean maps to 409
     * (no second created event), never 500 (quality review).
     */
    @PostMapping
    ResponseEntity<FacilityResponse> upsert(@RequestBody UpsertFacilityRequest request) {
        Facility facility;
        try {
            facility = new Facility(request.facilityId(), request.facilityName(),
                request.facilityTypeId());
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest().body(null);
        }
        try {
            operations.upsert(facility);
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(facility));
    }

    @GetMapping("/{facilityId}")
    ResponseEntity<FacilityResponse> get(@PathVariable String facilityId) {
        return facilityService.get(facilityId)
            .map(this::toResponse)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Delete: row removal + deleted event commit together. */
    @DeleteMapping("/{facilityId}")
    ResponseEntity<Void> delete(@PathVariable String facilityId) {
        if (!operations.delete(facilityId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    private FacilityResponse toResponse(Facility facility) {
        return new FacilityResponse(facility.facilityId(), facility.facilityName(),
            facility.facilityTypeId());
    }
}
