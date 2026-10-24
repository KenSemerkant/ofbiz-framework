package manufacturing.adapters.rest.persistence;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import manufacturing.application.RoutingStore;
import manufacturing.domain.Routing;

/** JPA adapter for the RoutingStore port. */
@Repository
public class JpaRoutingStore implements RoutingStore {

    private final RoutingRepository routings;

    public JpaRoutingStore(RoutingRepository routings) {
        this.routings = routings;
    }

    @Override
    public Optional<Routing> find(String routingId) {
        return routings.findById(routingId).map(this::toDomain);
    }

    @Override
    public void save(Routing routing) {
        routings.save(new RoutingEntity(routing.routingId(), routing.productId(),
            RoutingTaskJson.serialize(routing.tasks())));
    }

    private Routing toDomain(RoutingEntity entity) {
        return new Routing(entity.getRoutingId(), entity.getProductId(),
            RoutingTaskJson.deserialize(entity.getTasks()));
    }
}
