package workeffort.adapters.rest.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import workeffort.application.WorkEffortStore;
import workeffort.domain.WorkEffort;

/** JPA adapter for the WorkEffortStore port. */
@Repository
public class JpaWorkEffortStore implements WorkEffortStore {

    private final WorkEffortRepository efforts;

    public JpaWorkEffortStore(WorkEffortRepository efforts) {
        this.efforts = efforts;
    }

    @Override
    public Optional<WorkEffort> find(String workEffortId) {
        return efforts.findById(workEffortId).map(this::toDomain);
    }

    @Override
    public void save(WorkEffort effort) {
        efforts.save(new WorkEffortEntity(effort.workEffortId(), effort.workEffortTypeId(),
            effort.workEffortName(), effort.parentWorkEffortId(), effort.statusId(),
            effort.estimatedStartDate(), effort.estimatedCompletionDate()));
    }

    @Override
    public void delete(String workEffortId) {
        efforts.deleteById(workEffortId);
    }

    @Override
    public List<WorkEffort> findByParent(String parentWorkEffortId) {
        return efforts.findByParentWorkEffortId(parentWorkEffortId).stream()
            .map(this::toDomain).toList();
    }

    @Override
    public List<WorkEffort> findAllCalendarReady() {
        return efforts
            .findByEstimatedStartDateIsNotNullAndEstimatedCompletionDateIsNotNull()
            .stream().map(this::toDomain).toList();
    }

    @Override
    public List<WorkEffort> all() {
        return efforts.findAll().stream().map(this::toDomain).toList();
    }

    private WorkEffort toDomain(WorkEffortEntity entity) {
        return WorkEffort.rehydrate(entity.getWorkEffortId(), entity.getWorkEffortTypeId(),
            entity.getWorkEffortName(), entity.getParentWorkEffortId(), entity.getStatusId(),
            entity.getEstimatedStartDate(), entity.getEstimatedCompletionDate());
    }
}
