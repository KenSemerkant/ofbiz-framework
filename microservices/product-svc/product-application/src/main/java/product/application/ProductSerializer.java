package product.application;

import product.domain.Product;

/**
 * Serialization port: the application layer emits event payloads but stays
 * free of JSON framework types (ADR-007); the adapter layer supplies this.
 */
public interface ProductSerializer {

    String serialize(Product product);
}
