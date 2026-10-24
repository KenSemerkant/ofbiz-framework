package manufacturing.adapters.rest.persistence;

import org.springframework.stereotype.Component;
import manufacturing.application.ManufacturingSerializer;
import manufacturing.domain.Bom;
import manufacturing.domain.MrpProposal;
import manufacturing.domain.Routing;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Event payloads for the manufacturing.* topics (Jackson 3). */
@Component
public class JacksonManufacturingSerializer implements ManufacturingSerializer {

    private final ObjectMapper json = new ObjectMapper();

    @Override
    public String serializeBom(Bom bom) {
        ObjectNode node = json.createObjectNode();
        node.put("productId", bom.productId());
        ArrayNode components = node.putArray("components");
        for (Bom.Component component : bom.components()) {
            ObjectNode entry = components.addObject();
            entry.put("productId", component.productId());
            entry.put("quantity", component.quantity());
            entry.put("scrapPct", component.scrapPct());
        }
        return node.toString();
    }

    @Override
    public String serializeBomDeleted(String productId) {
        ObjectNode node = json.createObjectNode();
        node.put("productId", productId);
        return node.toString();
    }

    @Override
    public String serializeRouting(Routing routing) {
        ObjectNode node = json.createObjectNode();
        node.put("routingId", routing.routingId());
        node.put("productId", routing.productId());
        ArrayNode tasks = node.putArray("tasks");
        for (Routing.Task task : routing.tasks()) {
            ObjectNode entry = tasks.addObject();
            entry.put("seq", task.seq());
            entry.put("name", task.name());
            entry.put("estimatedDuration", task.estimatedDuration());
        }
        return node.toString();
    }

    @Override
    public String serializeMrpProposed(MrpProposal proposal) {
        ObjectNode node = json.createObjectNode();
        node.put("productId", proposal.productId());
        node.put("quantity", proposal.quantity());
        return node.toString();
    }
}
