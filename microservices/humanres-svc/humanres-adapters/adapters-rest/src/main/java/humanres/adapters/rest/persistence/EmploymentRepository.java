package humanres.adapters.rest.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmploymentRepository extends JpaRepository<EmploymentEntity, String> {
}
