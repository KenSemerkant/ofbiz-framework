package finance.adapters.rest.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GlAccountRepository extends JpaRepository<GlAccountEntity, String> {
}
