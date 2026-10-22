package product.application;

import product.domain.Category;

/**
 * Serialization port for category event payloads: the application layer stays
 * free of JSON framework types (ADR-007); the adapter layer supplies this
 * (quality review: hand-built JSON was injectable via categoryName).
 */
public interface CategorySerializer {

    String serialize(Category category);
}
