package order.application;

import java.util.Optional;
import order.domain.Order;

/** Persistence port for the Order aggregate (lines + history with it). */
public interface OrderStore {

    Optional<Order> find(String orderId);

    /** P9-F3: order listing for the SPA order area. */
    java.util.List<Order> all();

    void save(Order order);
}
