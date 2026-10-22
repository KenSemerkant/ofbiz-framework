package product.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "product_categories")
public class CategoryEntity {

    @Id
    @Column(name = "product_category_id")
    private String productCategoryId;

    @Column(name = "primary_parent_category_id")
    private String primaryParentCategoryId;

    @Column(name = "category_name", nullable = false)
    private String categoryName;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public CategoryEntity() {
    }

    public CategoryEntity(String productCategoryId, String primaryParentCategoryId,
            String categoryName) {
        this.productCategoryId = productCategoryId;
        this.primaryParentCategoryId = primaryParentCategoryId;
        this.categoryName = categoryName;
    }

    public String getProductCategoryId() {
        return productCategoryId;
    }

    public String getPrimaryParentCategoryId() {
        return primaryParentCategoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
