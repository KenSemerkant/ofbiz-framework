package billing.application;

import java.util.List;
import billing.domain.GatewayAttempt;

/** Append-only log of every gateway adapter call (reconciliation source). */
public interface GatewayAttemptStore {

    void append(GatewayAttempt attempt);

    List<GatewayAttempt> all();
}
