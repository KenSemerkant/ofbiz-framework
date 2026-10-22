package product.application;

import product.domain.Product;
import product.domain.ProductPrice;

/**
 * Serialization port: the application layer emits event payloads but stays
 * free of JSON framework types (ADR-007); the adapter layer supplies this.
 */
public interface ProductSerializer {

    String serialize(Product product);

    /** Deleted-event payload: {"productId": ...}. */
    String serializeDeleted(String productId);

    /**
     * Price-change payload; removed=true marks a price row that disappeared
     * with the update (quality review: hand-built JSON was injectable).
     */
    String serializePriceChange(String productId, ProductPrice price, boolean removed);
}
