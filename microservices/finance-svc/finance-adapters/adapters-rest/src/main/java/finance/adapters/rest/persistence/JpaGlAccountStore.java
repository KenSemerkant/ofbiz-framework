package finance.adapters.rest.persistence;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import finance.application.GlAccountStore;
import finance.domain.GlAccount;

/** JPA adapter for the GlAccountStore port. */
@Repository
public class JpaGlAccountStore implements GlAccountStore {

    private final GlAccountRepository accounts;

    public JpaGlAccountStore(GlAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public Optional<GlAccount> find(String glAccountId) {
        return accounts.findById(glAccountId).map(this::toDomain);
    }

    @Override
    public List<GlAccount> all() {
        // ponytail: in-memory sort — account counts are hundreds, not millions
        return accounts.findAll().stream().map(this::toDomain)
            .sorted(Comparator.comparing(GlAccount::accountCode)).toList();
    }

    @Override
    public void save(GlAccount account) {
        accounts.save(new GlAccountEntity(account.glAccountId(), account.accountCode(),
            account.accountName(), account.postBalance(), account.glAccountClassId()));
    }

    private GlAccount toDomain(GlAccountEntity entity) {
        return new GlAccount(entity.getGlAccountId(), entity.getAccountCode(),
            entity.getAccountName(), entity.getPostBalance(), entity.getGlAccountClassId());
    }
}
