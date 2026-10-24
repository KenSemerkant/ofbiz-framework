package manufacturing.application;

import java.util.List;
import manufacturing.domain.MrpEvent;

/** Persistence port for the MRP supply/demand ledger. */
public interface MrpEventStore {

    void save(MrpEvent event);

    List<MrpEvent> all();
}
