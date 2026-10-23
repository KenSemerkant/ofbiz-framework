package billing.adapters.rest.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingAccountRepository
        extends JpaRepository<BillingAccountEntity, String> {
}
