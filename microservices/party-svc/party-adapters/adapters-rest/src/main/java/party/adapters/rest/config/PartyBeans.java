package party.adapters.rest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import party.adapters.rest.persistence.JacksonPartySerializer;
import party.application.OutboxWriter;
import party.application.PartySerializer;
import party.application.PartyService;
import party.application.PartyStore;

/** Wires the application-layer use case into the adapter layer. */
@Configuration
@EnableScheduling
public class PartyBeans {

    @Bean
    PartySerializer partySerializer() {
        return new JacksonPartySerializer();
    }

    @Bean
    PartyService partyService(PartyStore store, OutboxWriter outbox, PartySerializer serializer) {
        return new PartyService(store, outbox, serializer);
    }

    @Bean
    party.application.MonolithProjectionService monolithProjectionService(
            party.application.MonolithProjectionSink sink) {
        return new party.application.MonolithProjectionService(sink);
    }
}
