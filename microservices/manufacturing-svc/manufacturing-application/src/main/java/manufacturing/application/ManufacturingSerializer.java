package manufacturing.application;

import manufacturing.domain.Bom;
import manufacturing.domain.MrpProposal;
import manufacturing.domain.Routing;

/** Event-payload port: the application layer never touches Jackson. */
public interface ManufacturingSerializer {

    String serializeBom(Bom bom);

    String serializeBomDeleted(String productId);

    String serializeRouting(Routing routing);

    String serializeMrpProposed(MrpProposal proposal);
}
