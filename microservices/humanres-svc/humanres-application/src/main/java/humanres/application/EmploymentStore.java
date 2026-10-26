package humanres.application;

import java.util.Optional;
import humanres.domain.Employment;

/** Persistence port for employments. */
public interface EmploymentStore {

    Optional<Employment> find(String employmentId);

    /** P9-F6: employment listing for the SPA HR area. */
    java.util.List<Employment> all();

    void save(Employment employment);
}
