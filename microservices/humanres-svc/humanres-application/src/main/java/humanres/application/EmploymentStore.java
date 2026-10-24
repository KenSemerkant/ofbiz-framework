package humanres.application;

import java.util.Optional;
import humanres.domain.Employment;

/** Persistence port for employments. */
public interface EmploymentStore {

    Optional<Employment> find(String employmentId);

    void save(Employment employment);
}
