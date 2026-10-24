package manufacturing.application;

import java.util.List;
import manufacturing.domain.MrpProposal;

/** Persistence port for MRP proposals (derived state, replaced per run). */
public interface MrpProposalStore {

    void replaceAll(List<MrpProposal> proposals);

    List<MrpProposal> all();
}
