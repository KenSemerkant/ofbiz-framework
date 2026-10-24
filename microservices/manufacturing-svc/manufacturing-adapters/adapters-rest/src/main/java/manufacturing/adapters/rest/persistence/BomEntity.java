package manufacturing.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** boms row: components inline as JSON (no cross-component query in P6). */
@Entity
@Table(name = "boms")
public class BomEntity {

    @Id
    @Column(name = "product_id")
    private String productId;

    @Column(nullable = false)
    private String components;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public BomEntity() {
    }

    public BomEntity(String productId, String components) {
        this.productId = productId;
        this.components = components;
    }

    public String getProductId() {
        return productId;
    }

    public String getComponents() {
        return components;
    }
}
