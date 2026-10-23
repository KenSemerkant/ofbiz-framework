package order.application;

import java.util.Optional;
import order.domain.Order;

/** Persistence port for the Order aggregate (lines + history with it). */
public interface OrderStore {

    Optional<Order> find(String orderId);

    void save(Order order);
}
