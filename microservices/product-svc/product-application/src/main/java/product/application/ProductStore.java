package product.application;

import java.util.Optional;
import product.domain.Product;

/** Persistence port for the Product aggregate. */
public interface ProductStore {

    Optional<Product> find(String productId);

    void save(Product product);
}
