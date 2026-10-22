package product.domain;

/**
 * A product category (OFBiz ProductCategory slice). The tree structure is the
 * primary parent pointer (OFBiz primaryParentCategoryId); a null parent means
 * a root category.
 */
public record Category(String productCategoryId, String primaryParentCategoryId,
        String categoryName) {

    public Category {
        if (productCategoryId == null || productCategoryId.isBlank()) {
            throw new IllegalArgumentException("productCategoryId must not be blank");
        }
        if (categoryName == null || categoryName.isBlank()) {
            throw new IllegalArgumentException("categoryName must not be blank");
        }
    }
}
