package billing.application;

import java.util.Optional;
import billing.domain.BillingAccount;

/** Persistence port for BillingAccount (P5-F1-S3). */
public interface BillingAccountStore {

    Optional<BillingAccount> find(String billingAccountId);

    void save(BillingAccount account);
}
