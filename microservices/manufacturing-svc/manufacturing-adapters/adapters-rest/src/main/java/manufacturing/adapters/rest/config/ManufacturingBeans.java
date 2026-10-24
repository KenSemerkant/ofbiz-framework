package manufacturing.adapters.rest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import manufacturing.application.BomService;
import manufacturing.application.ManufacturingSerializer;
import manufacturing.application.MrpService;
import manufacturing.application.OutboxWriter;
import manufacturing.application.RoutingService;

/** Wires the application-layer use cases into the adapter layer. */
@Configuration
@EnableScheduling
public class ManufacturingBeans {

    @Bean
    BomService bomService(manufacturing.application.BomStore boms, OutboxWriter outbox,
            ManufacturingSerializer serializer) {
        return new BomService(boms, outbox, serializer);
    }

    @Bean
    RoutingService routingService(manufacturing.application.RoutingStore routings,
            OutboxWriter outbox, ManufacturingSerializer serializer) {
        return new RoutingService(routings, outbox, serializer);
    }

    @Bean
    MrpService mrpService(manufacturing.application.MrpEventStore events,
            manufacturing.application.MrpProposalStore proposals, OutboxWriter outbox,
            ManufacturingSerializer serializer) {
        return new MrpService(events, proposals, outbox, serializer);
    }
}
