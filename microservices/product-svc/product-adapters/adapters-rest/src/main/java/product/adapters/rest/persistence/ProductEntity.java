package product.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "products")
public class ProductEntity {

    @Id
    @Column(name = "product_id")
    private String productId;

    @Column(name = "product_type_id", nullable = false)
    private String productTypeId;

    @Column(name = "internal_name", nullable = false)
    private String internalName;

    @Column(name = "product_name")
    private String productName;

    @Column(nullable = false)
    private String categories;

    @Column(nullable = false)
    private String assocs;

    @Column(nullable = false)
    private String prices;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public ProductEntity() {
    }

    public ProductEntity(String productId, String productTypeId, String internalName,
            String productName, String categories, String assocs, String prices) {
        this.productId = productId;
        this.productTypeId = productTypeId;
        this.internalName = internalName;
        this.productName = productName;
        this.categories = categories;
        this.assocs = assocs;
        this.prices = prices;
    }

    public String getProductId() {
        return productId;
    }

    public String getProductTypeId() {
        return productTypeId;
    }

    public String getInternalName() {
        return internalName;
    }

    public String getProductName() {
        return productName;
    }

    public String getCategories() {
        return categories;
    }

    public String getAssocs() {
        return assocs;
    }

    public String getPrices() {
        return prices;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
