package order.adapters.rest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import order.adapters.rest.persistence.JacksonOrderSerializer;
import order.application.AttributionService;
import order.application.AttributionStore;
import order.application.OrderEcaService;
import order.application.OrderLifecycleService;
import order.application.OrderSerializer;
import order.application.OrderStore;
import order.application.OutboxWriter;
import order.application.PlaceOrderSaga;
import order.application.QuoteService;
import order.application.QuoteStore;
import order.application.ReturnService;
import order.application.ReturnStore;
import order.application.SagaStore;
import order.application.InventoryGateway;
import order.application.PartyValidator;
import order.application.PriceSnapshotProvider;

/** Wires the application-layer use cases into the adapter layer. */
@Configuration
@EnableScheduling
public class OrderBeans {

    @Bean
    OrderSerializer orderSerializer() {
        return new JacksonOrderSerializer();
    }

    @Bean
    PlaceOrderSaga placeOrderSaga(OrderStore orders, SagaStore sagas, OutboxWriter outbox,
            OrderSerializer serializer, PartyValidator parties,
            PriceSnapshotProvider prices) {
        return new PlaceOrderSaga(orders, sagas, outbox, serializer, parties, prices);
    }

    @Bean
    OrderLifecycleService orderLifecycleService(OrderStore orders, OutboxWriter outbox,
            OrderSerializer serializer) {
        return new OrderLifecycleService(orders, outbox, serializer);
    }

    @Bean
    OrderEcaService orderEcaService(OrderStore orders, SagaStore sagas,
            InventoryGateway inventory, OutboxWriter outbox, OrderSerializer serializer) {
        return new OrderEcaService(orders, sagas, inventory, outbox, serializer);
    }

    @Bean
    QuoteService quoteService(QuoteStore quotes, PlaceOrderSaga placeOrder) {
        return new QuoteService(quotes, placeOrder);
    }

    @Bean
    ReturnService returnService(ReturnStore returns, OrderStore orders, SagaStore sagas,
            InventoryGateway inventory, OutboxWriter outbox, OrderSerializer serializer) {
        return new ReturnService(returns, orders, sagas, inventory, outbox, serializer);
    }

    @Bean
    AttributionService attributionService(AttributionStore attributions) {
        return new AttributionService(attributions);
    }
}
