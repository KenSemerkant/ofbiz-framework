package workeffort.adapters.rest.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TimesheetRepository extends JpaRepository<TimesheetEntity, String> {
}
