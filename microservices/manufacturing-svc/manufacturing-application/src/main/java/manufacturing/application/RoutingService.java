package manufacturing.application;

import java.util.NoSuchElementException;
import java.util.Optional;
import manufacturing.domain.Routing;

/**
 * P6-T1 routing use case: create/update with one outbox event per
 * mutation (created/updated only — routings are not deleted in v1, they
 * are replaced by a new revision).
 */
public class RoutingService {

    private final RoutingStore routings;
    private final OutboxWriter outbox;
    private final ManufacturingSerializer serializer;

    public RoutingService(RoutingStore routings, OutboxWriter outbox,
            ManufacturingSerializer serializer) {
        this.routings = routings;
        this.outbox = outbox;
        this.serializer = serializer;
    }

    public Routing create(String routingId, String productId,
            java.util.List<Routing.Task> tasks) {
        if (routings.find(routingId).isPresent()) {
            throw new IllegalStateException("routing already exists " + routingId);
        }
        Routing routing = new Routing(routingId, productId, tasks);
        routings.save(routing);
        outbox.append(Routing.CREATED, routingId, serializer.serializeRouting(routing));
        return routing;
    }

    public Routing update(String routingId, String productId,
            java.util.List<Routing.Task> tasks) {
        if (routings.find(routingId).isEmpty()) {
            throw new NoSuchElementException("no routing " + routingId);
        }
        Routing routing = new Routing(routingId, productId, tasks);
        routings.save(routing);
        outbox.append(Routing.UPDATED, routingId, serializer.serializeRouting(routing));
        return routing;
    }

    public Optional<Routing> get(String routingId) {
        return routings.find(routingId);
    }
}
