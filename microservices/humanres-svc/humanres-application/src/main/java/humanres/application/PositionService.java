package humanres.application;

import java.util.NoSuchElementException;
import java.util.Optional;
import humanres.domain.EmplPosition;

/**
 * P6-T3 position use case: CRUD over the EmplPosition live slice with one
 * outbox event per create/update. A reporting parent must exist; ids are
 * caller-supplied so a recreate is a typed conflict.
 */
public class PositionService {

    public static final String POSITION_CREATED = "humanres.position.created.v1";
    public static final String POSITION_UPDATED = "humanres.position.updated.v1";

    private final EmplPositionStore positions;
    private final OutboxWriter outbox;
    private final HumanresSerializer serializer;

    public PositionService(EmplPositionStore positions, OutboxWriter outbox,
            HumanresSerializer serializer) {
        this.positions = positions;
        this.outbox = outbox;
        this.serializer = serializer;
    }

    public Optional<EmplPosition> find(String emplPositionId) {
        return positions.find(emplPositionId);
    }

    public EmplPosition create(String emplPositionId, String emplPositionTypeId,
            String reportingToEmplPositionId, String statusId) {
        if (positions.find(emplPositionId).isPresent()) {
            throw new DuplicatePositionException(emplPositionId);
        }
        EmplPosition position = new EmplPosition(emplPositionId, emplPositionTypeId,
            reportingToEmplPositionId, statusId);
        requireParent(reportingToEmplPositionId);
        positions.save(position);
        outbox.append(POSITION_CREATED, emplPositionId, serializer.serializePosition(position));
        return position;
    }

    public EmplPosition update(String emplPositionId, String emplPositionTypeId,
            String reportingToEmplPositionId, String statusId) {
        positions.find(emplPositionId)
            .orElseThrow(() -> new NoSuchElementException("no empl position " + emplPositionId));
        requireParent(reportingToEmplPositionId);
        EmplPosition position = new EmplPosition(emplPositionId, emplPositionTypeId,
            reportingToEmplPositionId, statusId);
        positions.save(position);
        outbox.append(POSITION_UPDATED, emplPositionId, serializer.serializePosition(position));
        return position;
    }

    private void requireParent(String reportingToEmplPositionId) {
        if (reportingToEmplPositionId != null && positions.find(reportingToEmplPositionId)
                .isEmpty()) {
            throw new NoSuchElementException(
                "no empl position " + reportingToEmplPositionId);
        }
    }
}
