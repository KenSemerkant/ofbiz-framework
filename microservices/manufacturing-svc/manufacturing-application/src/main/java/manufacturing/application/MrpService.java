package manufacturing.application;

import java.util.List;
import manufacturing.domain.MrpEvent;
import manufacturing.domain.MrpPlanner;
import manufacturing.domain.MrpProposal;

/**
 * P6-T1 MRP use case: consumers append supply/demand facts to the ledger;
 * a run nets them deterministically (MrpPlanner), replaces the proposal
 * set, and emits one manufacturing.mrp.proposed.v1 per short product —
 * all in the caller's transaction (ADR-009).
 */
public class MrpService {

    public static final String MRP_PROPOSED = "manufacturing.mrp.proposed.v1";

    private final MrpEventStore events;
    private final MrpProposalStore proposals;
    private final OutboxWriter outbox;
    private final ManufacturingSerializer serializer;

    public MrpService(MrpEventStore events, MrpProposalStore proposals, OutboxWriter outbox,
            ManufacturingSerializer serializer) {
        this.events = events;
        this.proposals = proposals;
        this.outbox = outbox;
        this.serializer = serializer;
    }

    /** Ledger append; the consumer owns processed_events idempotency. */
    public void recordEvent(MrpEvent event) {
        events.save(event);
    }

    public List<MrpEvent> events() {
        return events.all();
    }

    public List<MrpProposal> proposals() {
        return proposals.all();
    }

    /** Deterministic netting; proposals are derived state, replaced per run. */
    public List<MrpProposal> run() {
        List<MrpProposal> result = MrpPlanner.net(events.all());
        proposals.replaceAll(result);
        for (MrpProposal proposal : result) {
            outbox.append(MRP_PROPOSED, proposal.productId(),
                serializer.serializeMrpProposed(proposal));
        }
        return result;
    }
}
