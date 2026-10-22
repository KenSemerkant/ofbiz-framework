package inventory.adapters.rest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import inventory.adapters.rest.persistence.JacksonInventorySerializer;
import inventory.application.FacilityService;
import inventory.application.FacilityStore;
import inventory.application.InventorySerializer;
import inventory.application.InventoryService;
import inventory.application.InventoryItemStore;
import inventory.application.OutboxWriter;

/** Wires the application-layer use cases into the adapter layer. */
@Configuration
@EnableScheduling
public class InventoryBeans {

    @Bean
    InventorySerializer inventorySerializer() {
        return new JacksonInventorySerializer();
    }

    @Bean
    FacilityService facilityService(FacilityStore facilities, OutboxWriter outbox,
            InventorySerializer serializer) {
        return new FacilityService(facilities, outbox, serializer);
    }

    @Bean
    InventoryService inventoryService(InventoryItemStore items, OutboxWriter outbox,
            InventorySerializer serializer) {
        return new InventoryService(items, outbox, serializer);
    }
}
