package humanres.application;

import java.util.Optional;
import humanres.domain.EmplPosition;

/** Persistence port for positions. */
public interface EmplPositionStore {

    Optional<EmplPosition> find(String emplPositionId);

    /** P9-F6: position listing for the SPA HR area. */
    java.util.List<EmplPosition> all();

    void save(EmplPosition position);
}
