package identity.adapters.rest.events;

import identity.application.RecordLoginAuditService;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * P1-F1-S2 AC3: every successful user login lands an audit event in the
 * outbox. Only interactive form logins count — OAuth2 client
 * authentications (token endpoint) are not user logins.
 */
@Component
public class LoginAuditListener {

    private final RecordLoginAuditService audit;

    public LoginAuditListener(RecordLoginAuditService audit) {
        this.audit = audit;
    }

    @EventListener
    public void on(AuthenticationSuccessEvent event) {
        if (event.getSource() instanceof UsernamePasswordAuthenticationToken token && token.isAuthenticated()) {
            audit.loginSucceeded(token.getName());
        }
    }
}
