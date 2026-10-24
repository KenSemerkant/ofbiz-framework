package humanres.adapters.rest.persistence;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import humanres.application.EmplPositionStore;
import humanres.domain.EmplPosition;

/** JPA adapter for the EmplPositionStore port. */
@Repository
public class JpaEmplPositionStore implements EmplPositionStore {

    private final EmplPositionRepository positions;

    public JpaEmplPositionStore(EmplPositionRepository positions) {
        this.positions = positions;
    }

    @Override
    public Optional<EmplPosition> find(String emplPositionId) {
        return positions.findById(emplPositionId).map(this::toDomain);
    }

    @Override
    public void save(EmplPosition position) {
        positions.save(new EmplPositionEntity(position.emplPositionId(),
            position.emplPositionTypeId(), position.reportingToEmplPositionId(),
            position.statusId()));
    }

    private EmplPosition toDomain(EmplPositionEntity entity) {
        return new EmplPosition(entity.getEmplPositionId(), entity.getEmplPositionTypeId(),
            entity.getReportingToEmplPositionId(), entity.getStatusId());
    }
}
