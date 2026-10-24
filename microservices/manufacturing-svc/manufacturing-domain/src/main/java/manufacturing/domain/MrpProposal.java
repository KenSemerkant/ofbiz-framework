package manufacturing.domain;

import java.math.BigDecimal;

/**
 * P6-T1 MRP output (monolith ProposedOrder/Requirement, product-level for
 * v1): the netted shortage for one product — proposed as a replenishment
 * order on manufacturing.mrp.proposed.v1.
 */
public record MrpProposal(String productId, BigDecimal quantity) {

    public MrpProposal {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("proposal quantity must be positive");
        }
    }
}
