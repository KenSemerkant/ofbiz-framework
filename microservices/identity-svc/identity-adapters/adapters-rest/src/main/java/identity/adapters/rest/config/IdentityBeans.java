package identity.adapters.rest.config;

import identity.application.PartyProjectionService;
import identity.application.RecordLoginAuditService;
import identity.application.UserAdministrationService;
import identity.application.UserLoginStore;
import identity.application.OutboxWriter;
import identity.application.PartyProjectionStore;
import identity.adapters.rest.security.HashCryptPasswordEncoder;
import identity.adapters.rest.security.OfbizPasswordHasher;
import identity.domain.PasswordHasher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Wires the domain/application services into the adapter layer. */
@Configuration
@EnableScheduling
public class IdentityBeans {

    @Bean
    PasswordHasher passwordHasher() {
        return new OfbizPasswordHasher();
    }

    @Bean
    PasswordEncoder passwordEncoder(PasswordHasher hasher) {
        return new HashCryptPasswordEncoder(hasher);
    }

    @Bean
    UserAdministrationService userAdministrationService(UserLoginStore store, PasswordHasher hasher) {
        return new UserAdministrationService(store, hasher);
    }

    @Bean
    RecordLoginAuditService recordLoginAuditService(OutboxWriter outbox) {
        return new RecordLoginAuditService(outbox);
    }

    @Bean
    PartyProjectionService partyProjectionService(PartyProjectionStore store) {
        return new PartyProjectionService(store);
    }
}
