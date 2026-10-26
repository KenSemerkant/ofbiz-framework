package inventory.application;

import java.util.Optional;
import inventory.domain.Facility;

/** Persistence port for the Facility aggregate. */
public interface FacilityStore {

    Optional<Facility> find(String facilityId);

    void save(Facility facility);

    /** @return true if a row existed and was removed. */
    boolean delete(String facilityId);

    /** P9-F2: facility listing for the SPA catalog area. */
    java.util.List<Facility> all();
}
