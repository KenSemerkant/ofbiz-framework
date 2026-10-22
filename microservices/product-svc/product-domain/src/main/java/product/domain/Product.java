package product.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Product aggregate root (P3-F1-S1): identity, names, category memberships
 * and outbound associations. Mutators return whether state changed so the
 * application layer can emit the matching domain event exactly once.
 */
public final class Product {

    private final String productId;
    private final String productTypeId;
    private final String internalName;
    private final String productName;
    private final Set<String> categoryIds = new LinkedHashSet<>();
    private final List<ProductAssoc> associations = new ArrayList<>();
    private final List<ProductPrice> prices = new ArrayList<>();

    public Product(String productId, String productTypeId, String internalName,
            String productName, Collection<String> categoryIds,
            Collection<ProductAssoc> associations) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }
        if (productTypeId == null || productTypeId.isBlank()) {
            throw new IllegalArgumentException("productTypeId must not be blank");
        }
        if (internalName == null || internalName.isBlank()) {
            throw new IllegalArgumentException("internalName must not be blank");
        }
        this.productId = productId;
        this.productTypeId = productTypeId;
        this.internalName = internalName;
        this.productName = productName;
        if (categoryIds != null) {
            categoryIds.forEach(this::addToCategory);
        }
        if (associations != null) {
            associations.forEach(this::associateWith);
        }
    }

    /** @return true if the membership is new. */
    public boolean addToCategory(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            throw new IllegalArgumentException("categoryId must not be blank");
        }
        return categoryIds.add(categoryId);
    }

    /** @return true if a membership with this category existed and is now removed. */
    public boolean removeFromCategory(String categoryId) {
        return categoryIds.remove(categoryId);
    }

    /** @return true if the association is new. */
    public boolean associateWith(ProductAssoc assoc) {
        if (assoc == null) {
            throw new IllegalArgumentException("assoc must not be null");
        }
        if (assoc.productIdTo().equals(productId)) {
            throw new IllegalArgumentException("a product cannot associate with itself");
        }
        if (associations.contains(assoc)) {
            return false;
        }
        return associations.add(assoc);
    }

    public List<ProductAssoc> activeAssociations(Instant at) {
        return associations.stream().filter(a -> a.activeAt(at)).toList();
    }

    /** @return true if the price row is new (P3-F1-S2). */
    public boolean addPrice(ProductPrice price) {
        if (price == null) {
            throw new IllegalArgumentException("price must not be null");
        }
        if (prices.contains(price)) {
            return false;
        }
        return prices.add(price);
    }

    public String productId() {
        return productId;
    }

    public String productTypeId() {
        return productTypeId;
    }

    public String internalName() {
        return internalName;
    }

    public String productName() {
        return productName;
    }

    public Set<String> categoryIds() {
        return Set.copyOf(categoryIds);
    }

    public List<ProductAssoc> associations() {
        return List.copyOf(associations);
    }

    public List<ProductPrice> prices() {
        return List.copyOf(prices);
    }
}
