package humanres.adapters.rest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import humanres.application.EmploymentService;
import humanres.application.HumanresSerializer;
import humanres.application.OnboardingSaga;
import humanres.application.OnboardingSagaStore;
import humanres.application.OutboxWriter;
import humanres.application.PartyProjectionStore;
import humanres.application.PartyRoleClient;
import humanres.application.PositionService;

/** Wires the application-layer use cases into the adapter layer. */
@Configuration
@EnableScheduling
public class HumanresBeans {

    @Bean
    PositionService positionService(humanres.application.EmplPositionStore positions,
            OutboxWriter outbox, HumanresSerializer serializer) {
        return new PositionService(positions, outbox, serializer);
    }

    @Bean
    EmploymentService employmentService(humanres.application.EmploymentStore employments,
            humanres.application.EmplPositionStore positions, OutboxWriter outbox,
            HumanresSerializer serializer) {
        return new EmploymentService(employments, positions, outbox, serializer);
    }

    @Bean
    OnboardingSaga onboardingSaga(OnboardingSagaStore sagas,
            PartyProjectionStore parties, humanres.application.EmplPositionStore positions,
            PartyRoleClient partyRoles, EmploymentService employments, OutboxWriter outbox,
            HumanresSerializer serializer) {
        return new OnboardingSaga(sagas, parties, positions, partyRoles, employments, outbox,
            serializer);
    }
}
