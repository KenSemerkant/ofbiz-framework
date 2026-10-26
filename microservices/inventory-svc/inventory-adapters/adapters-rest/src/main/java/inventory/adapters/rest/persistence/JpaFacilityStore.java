package inventory.adapters.rest.persistence;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import inventory.application.FacilityStore;
import inventory.domain.Facility;

/** JPA adapter for the FacilityStore port. */
@Repository
public class JpaFacilityStore implements FacilityStore {

    private final FacilityRepository facilities;

    public JpaFacilityStore(FacilityRepository facilities) {
        this.facilities = facilities;
    }

    @Override
    public Optional<Facility> find(String facilityId) {
        return facilities.findById(facilityId).map(this::toDomain);
    }

    @Override
    public void save(Facility facility) {
        facilities.save(new FacilityEntity(facility.facilityId(), facility.facilityName(),
            facility.facilityTypeId()));
    }

    @Override
    public java.util.List<Facility> all() {
        return facilities.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public boolean delete(String facilityId) {
        if (!facilities.existsById(facilityId)) {
            return false;
        }
        facilities.deleteById(facilityId);
        return true;
    }

    private Facility toDomain(FacilityEntity entity) {
        return new Facility(entity.getFacilityId(), entity.getFacilityName(),
            entity.getFacilityTypeId());
    }
}
