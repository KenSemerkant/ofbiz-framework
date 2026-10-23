package order.application;

import java.util.Optional;
import order.domain.ReturnOrder;

/** Persistence port for returns. */
public interface ReturnStore {

    Optional<ReturnOrder> find(String returnId);

    void save(ReturnOrder returns);
}
