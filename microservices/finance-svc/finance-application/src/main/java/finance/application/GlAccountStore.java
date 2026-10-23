package finance.application;

import java.util.Optional;
import finance.domain.GlAccount;

/** Persistence port for the chart of accounts. */
public interface GlAccountStore {

    Optional<GlAccount> find(String glAccountId);

    void save(GlAccount account);
}
