package billing.adapters.rest.persistence;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import billing.application.BillingAccountStore;
import billing.domain.BillingAccount;

/** JPA adapter for the BillingAccountStore port. */
@Repository
public class JpaBillingAccountStore implements BillingAccountStore {

    private final BillingAccountRepository accounts;

    public JpaBillingAccountStore(BillingAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public Optional<BillingAccount> find(String billingAccountId) {
        return accounts.findById(billingAccountId).map(this::toDomain);
    }

    @Override
    public void save(BillingAccount account) {
        accounts.save(new BillingAccountEntity(account.billingAccountId(), account.customerId(),
            account.creditLimit(), account.outstandingArBalance(), account.netDaysTerm()));
    }

    private BillingAccount toDomain(BillingAccountEntity entity) {
        return BillingAccount.rehydrate(entity.getBillingAccountId(), entity.getCustomerId(),
            entity.getCreditLimit(), entity.getOutstandingArBalance(), entity.getNetDaysTerm());
    }
}
