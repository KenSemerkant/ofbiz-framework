package workeffort.adapters.rest.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkEffortRepository extends JpaRepository<WorkEffortEntity, String> {

    List<WorkEffortEntity> findByParentWorkEffortId(String parentWorkEffortId);

    /** Calendar-feed input: both estimate dates present (P6-T2 CG). */
    List<WorkEffortEntity> findByEstimatedStartDateIsNotNullAndEstimatedCompletionDateIsNotNull();
}
