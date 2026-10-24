package marketing.adapters.rest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import marketing.application.AttributionService;
import marketing.application.MarketingSerializer;
import marketing.application.MarketingService;
import marketing.application.OutboxWriter;

/** Wires the application-layer use cases into the adapter layer. */
@Configuration
@EnableScheduling
public class MarketingBeans {

    @Bean
    MarketingService marketingService(
            marketing.application.MarketingCampaignStore campaigns,
            marketing.application.TrackingCodeStore trackingCodes,
            marketing.application.SalesOpportunityStore opportunities, OutboxWriter outbox,
            MarketingSerializer serializer) {
        return new MarketingService(campaigns, trackingCodes, opportunities, outbox, serializer);
    }

    @Bean
    AttributionService attributionService(
            marketing.application.AttributionStore attributions,
            marketing.application.TrackingCodeStore trackingCodes) {
        return new AttributionService(attributions, trackingCodes);
    }
}
