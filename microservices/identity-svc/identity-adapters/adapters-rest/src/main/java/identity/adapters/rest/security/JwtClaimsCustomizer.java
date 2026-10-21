package identity.adapters.rest.security;

import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

/**
 * P2-F3-S1: access tokens carry `roles` and `party_id` claims resolved from
 * the party projection. Issued for user principals; client_credentials
 * machine tokens (e.g. the gateway) carry none.
 */
@Configuration
public class JwtClaimsCustomizer {

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> partyRolesCustomizer(RoleClaimResolver resolver) {
        return context -> {
            if (!"access_token".equals(context.getTokenType().getValue())) {
                return;
            }
            String principal = context.getPrincipal().getName();
            Set<String> roles = resolver.resolve(principal);
            if (roles.isEmpty()) {
                return; // unknown/machine principal — default claims only
            }
            context.getClaims().claim("roles", roles);
            resolver.partyIdOf(principal)
                .ifPresent(partyId -> context.getClaims().claim("party_id", partyId));
        };
    }
}
