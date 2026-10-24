package manufacturing.application;

import java.util.Optional;
import manufacturing.domain.Routing;

/** Persistence port for production routings. */
public interface RoutingStore {

    Optional<Routing> find(String routingId);

    void save(Routing routing);
}
