package order.adapters.rest.persistence;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import order.application.OrderStore;
import order.domain.Order;
import order.domain.OrderStatus;

/** JPA adapter for the OrderStore port. */
@Repository
public class JpaOrderStore implements OrderStore {

    private final OrderRepository orders;

    public JpaOrderStore(OrderRepository orders) {
        this.orders = orders;
    }

    @Override
    public java.util.List<Order> all() {
        return orders.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Order> find(String orderId) {
        return orders.findById(orderId).map(this::toDomain);
    }

    @Override
    public void save(Order order) {
        orders.save(new OrderEntity(order.orderId(), order.customerId(), order.trackingCode(),
            order.status().name(), OrderJson.serializeLines(order.lines()),
            OrderJson.serializeHistory(order.history()), order.grandTotal(),
            order.lines().get(0).currencyUomId(),
            order.history().isEmpty() ? null : order.history().get(0).changedAt()));
    }

    private Order toDomain(OrderEntity entity) {
        return Order.rehydrate(entity.getOrderId(), entity.getCustomerId(),
            entity.getTrackingCode(), OrderJson.deserializeLines(entity.getLines()),
            OrderStatus.valueOf(entity.getStatus()),
            OrderJson.deserializeHistory(entity.getHistory()));
    }
}
