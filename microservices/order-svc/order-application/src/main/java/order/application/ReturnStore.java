package order.application;

import java.util.Optional;
import order.domain.ReturnOrder;

/** Persistence port for returns. */
public interface ReturnStore {

    Optional<ReturnOrder> find(String returnId);

    /** P9-F3: return listing for the SPA order area. */
    java.util.List<ReturnOrder> all();

    void save(ReturnOrder returns);
}
