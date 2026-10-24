package humanres.application;

import java.util.Optional;
import humanres.domain.EmplPosition;

/** Persistence port for positions. */
public interface EmplPositionStore {

    Optional<EmplPosition> find(String emplPositionId);

    void save(EmplPosition position);
}
