package manufacturing.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * P6-T1 deterministic netting (the MrpServices loop, product-level for
 * v1): per product, events ordered by occurredAt then eventId feed a
 * running position — supply adds, demand subtracts (mirrors the monolith
 * stockTmp accumulation with minimumStock 0). A product that ends short
 * gets exactly one proposal for the shortage. Same dataset in, same
 * proposals out — that determinism is the CG-6 acceptance check.
 */
public final class MrpPlanner {

    public static List<MrpProposal> net(List<MrpEvent> events) {
        Map<String, List<MrpEvent>> byProduct = new TreeMap<>();
        for (MrpEvent event : events) {
            byProduct.computeIfAbsent(event.productId(), p -> new ArrayList<>()).add(event);
        }
        List<MrpProposal> proposals = new ArrayList<>();
        for (Map.Entry<String, List<MrpEvent>> entry : byProduct.entrySet()) {
            List<MrpEvent> ordered = entry.getValue().stream()
                .sorted(Comparator.comparing(MrpEvent::occurredAt)
                    .thenComparing(MrpEvent::eventId))
                .toList();
            BigDecimal position = BigDecimal.ZERO;
            for (MrpEvent event : ordered) {
                position = event.eventType() == MrpEventType.SUPPLY
                    ? position.add(event.quantity())
                    : position.subtract(event.quantity());
            }
            if (position.signum() < 0) {
                proposals.add(new MrpProposal(entry.getKey(), position.negate()));
            }
        }
        return proposals;
    }

    private MrpPlanner() {
    }
}
