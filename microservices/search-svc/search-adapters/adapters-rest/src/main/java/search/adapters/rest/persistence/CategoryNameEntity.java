package search.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Latest name per category, fed by product.category.updated.v1 events. */
@Entity
@Table(name = "product_categories")
public class CategoryNameEntity {

    @Id
    @Column(name = "product_category_id")
    private String productCategoryId;

    @Column(name = "category_name", nullable = false)
    private String categoryName;

    public CategoryNameEntity() {
    }

    public CategoryNameEntity(String productCategoryId, String categoryName) {
        this.productCategoryId = productCategoryId;
        this.categoryName = categoryName;
    }

    public void rename(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getProductCategoryId() {
        return productCategoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }
}
