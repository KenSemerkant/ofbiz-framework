package humanres.adapters.rest.persistence;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import humanres.application.EmploymentStore;
import humanres.domain.Employment;

/** JPA adapter for the EmploymentStore port. */
@Repository
public class JpaEmploymentStore implements EmploymentStore {

    private final EmploymentRepository employments;

    public JpaEmploymentStore(EmploymentRepository employments) {
        this.employments = employments;
    }

    @Override
    public java.util.List<Employment> all() {
        return employments.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Employment> find(String employmentId) {
        return employments.findById(employmentId).map(this::toDomain);
    }

    @Override
    public void save(Employment employment) {
        employments.save(new EmploymentEntity(employment.employmentId(), employment.partyId(),
            employment.emplPositionId(), employment.fromDate(), employment.thruDate()));
    }

    private Employment toDomain(EmploymentEntity entity) {
        return new Employment(entity.getEmploymentId(), entity.getPartyId(),
            entity.getEmplPositionId(), entity.getFromDate(), entity.getThruDate());
    }
}
