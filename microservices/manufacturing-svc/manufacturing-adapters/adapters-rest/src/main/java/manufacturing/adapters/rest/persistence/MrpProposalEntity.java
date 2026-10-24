package manufacturing.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** mrp_proposals row (P6-T1): derived state, replaced by each MRP run. */
@Entity
@Table(name = "mrp_proposals")
public class MrpProposalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public MrpProposalEntity() {
    }

    public MrpProposalEntity(String productId, BigDecimal quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public String getProductId() {
        return productId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }
}
