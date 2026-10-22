package product.adapters.rest.persistence;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import product.application.ProductStore;
import product.domain.Product;

/** JPA adapter for the ProductStore port. */
@Repository
public class JpaProductStore implements ProductStore {

    private final ProductRepository products;

    public JpaProductStore(ProductRepository products) {
        this.products = products;
    }

    @Override
    public Optional<Product> find(String productId) {
        return products.findById(productId).map(this::toDomain);
    }

    @Override
    public void save(Product product) {
        products.save(new ProductEntity(product.productId(), product.productTypeId(),
            product.internalName(), product.productName(),
            ProductJson.serializeCategories(product.categoryIds()),
            ProductJson.serializeAssocs(product.associations()),
            ProductJson.serializePrices(product.prices())));
    }

    private Product toDomain(ProductEntity entity) {
        Product product = new Product(entity.getProductId(), entity.getProductTypeId(),
            entity.getInternalName(), entity.getProductName(),
            ProductJson.deserializeCategories(entity.getCategories()), null);
        ProductJson.deserializeAssocs(entity.getAssocs()).forEach(product::associateWith);
        ProductJson.deserializePrices(entity.getPrices()).forEach(product::addPrice);
        return product;
    }
}
