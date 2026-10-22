package product.application;

import java.util.Optional;
import product.domain.Product;

/** Persistence port for the Product aggregate. */
public interface ProductStore {

    Optional<Product> find(String productId);

    void save(Product product);

    /** @return true if a row existed and was removed. */
    boolean delete(String productId);

    /** @return true if any product is a member of the category. */
    boolean anyInCategory(String categoryId);
}
