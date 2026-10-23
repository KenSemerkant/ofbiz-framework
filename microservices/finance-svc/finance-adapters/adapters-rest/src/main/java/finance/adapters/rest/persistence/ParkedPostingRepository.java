package finance.adapters.rest.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkedPostingRepository extends JpaRepository<ParkedPostingEntity, Long> {
}
