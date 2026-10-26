package manufacturing.application;

import java.util.Optional;
import manufacturing.domain.Routing;

/** Persistence port for production routings. */
public interface RoutingStore {

    Optional<Routing> find(String routingId);

    /** P9-F8: routing listing for the SPA manufacturing area. */
    java.util.List<Routing> all();

    void save(Routing routing);
}
