package manufacturing.adapters.rest.persistence;

import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import manufacturing.application.MrpProposalStore;
import manufacturing.domain.MrpProposal;

/** JPA adapter for the MrpProposalStore port. */
@Repository
public class JpaMrpProposalStore implements MrpProposalStore {

    private final MrpProposalRepository proposals;

    public JpaMrpProposalStore(MrpProposalRepository proposals) {
        this.proposals = proposals;
    }

    @Override
    @Transactional
    public void replaceAll(List<MrpProposal> replacements) {
        proposals.deleteAllInBatch();
        proposals.saveAll(replacements.stream()
            .map(p -> new MrpProposalEntity(p.productId(), p.quantity()))
            .toList());
    }

    @Override
    public List<MrpProposal> all() {
        return proposals.findAll().stream()
            .map(e -> new MrpProposal(e.getProductId(), e.getQuantity()))
            .toList();
    }
}
