package workeffort.application;

import java.util.List;
import java.util.Optional;
import workeffort.domain.WorkEffort;

/** Persistence port for the WorkEffort aggregate. */
public interface WorkEffortStore {

    Optional<WorkEffort> find(String workEffortId);

    void save(WorkEffort effort);

    void delete(String workEffortId);

    /** One tree level: the children of parentWorkEffortId (?parent= query). */
    List<WorkEffort> findByParent(String parentWorkEffortId);

    /** Calendar-feed input: efforts with both estimate dates present. */
    List<WorkEffort> findAllCalendarReady();

    List<WorkEffort> all();
}
