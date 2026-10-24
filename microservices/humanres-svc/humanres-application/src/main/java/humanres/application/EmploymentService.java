package humanres.application;

import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;
import humanres.domain.Employment;

/**
 * P6-T3 employment use case: a party occupying a position, one outbox event
 * per create. Updates only move the thru date (employment ends) — history
 * is never rewritten.
 */
public class EmploymentService {

    public static final String EMPLOYMENT_CREATED = "humanres.employment.created.v1";

    private final EmploymentStore employments;
    private final EmplPositionStore positions;
    private final OutboxWriter outbox;
    private final HumanresSerializer serializer;

    public EmploymentService(EmploymentStore employments, EmplPositionStore positions,
            OutboxWriter outbox, HumanresSerializer serializer) {
        this.employments = employments;
        this.positions = positions;
        this.outbox = outbox;
        this.serializer = serializer;
    }

    public Optional<Employment> find(String employmentId) {
        return employments.find(employmentId);
    }

    public Employment create(String employmentId, String partyId, String emplPositionId,
            LocalDate fromDate, LocalDate thruDate) {
        if (employments.find(employmentId).isPresent()) {
            throw new DuplicateEmploymentException(employmentId);
        }
        positions.find(emplPositionId)
            .orElseThrow(() -> new NoSuchElementException("no empl position " + emplPositionId));
        Employment employment = new Employment(employmentId, partyId, emplPositionId, fromDate,
            thruDate);
        employments.save(employment);
        outbox.append(EMPLOYMENT_CREATED, employmentId,
            serializer.serializeEmployment(employment));
        return employment;
    }

    /** End (or re-open with null) an employment: only the thru date moves. */
    public Employment updateThruDate(String employmentId, LocalDate thruDate) {
        Employment existing = employments.find(employmentId)
            .orElseThrow(() -> new NoSuchElementException("no employment " + employmentId));
        Employment updated = new Employment(existing.employmentId(), existing.partyId(),
            existing.emplPositionId(), existing.fromDate(), thruDate);
        employments.save(updated);
        return updated;
    }
}
