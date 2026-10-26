package finance.application;

import java.util.List;
import java.util.Optional;
import finance.domain.GlAccount;

/** Persistence port for the chart of accounts. */
public interface GlAccountStore {

    Optional<GlAccount> find(String glAccountId);

    /** P9-F1: chart-of-accounts listing for the SPA accounting area. */
    List<GlAccount> all();

    void save(GlAccount account);
}
