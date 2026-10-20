package identity.application;

/**
 * P1-F1-S2 AC3: successful user logins produce an audit event via the
 * transactional outbox (relayed to audit.auditevent.recorded.v1 by the
 * adapters' publisher).
 */
public class RecordLoginAuditService {

    private final OutboxWriter outbox;

    public RecordLoginAuditService(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    public void loginSucceeded(String userLoginId) {
        outbox.append("identity.login.succeeded",
            "{\"userLoginId\":\"" + userLoginId + "\",\"severity\":\"INFO\"}");
    }
}
