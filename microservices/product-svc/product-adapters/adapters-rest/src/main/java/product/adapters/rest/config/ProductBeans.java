package product.adapters.rest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import product.adapters.rest.persistence.JacksonCategorySerializer;
import product.adapters.rest.persistence.JacksonProductSerializer;
import product.application.CategoryService;
import product.application.CategoryStore;
import product.application.OutboxWriter;
import product.application.ProductSerializer;
import product.application.ProductService;
import product.application.ProductStore;

/** Wires the application-layer use cases into the adapter layer. */
@Configuration
@EnableScheduling
public class ProductBeans {

    @Bean
    ProductSerializer productSerializer() {
        return new JacksonProductSerializer();
    }

    @Bean
    product.application.CategorySerializer categorySerializer() {
        return new JacksonCategorySerializer();
    }

    @Bean
    ProductService productService(ProductStore store, OutboxWriter outbox,
            ProductSerializer serializer,
            @org.springframework.beans.factory.annotation.Value(
                "${product.default-currency:USD}") String defaultCurrencyUomId) {
        return new ProductService(store, outbox, serializer, defaultCurrencyUomId);
    }

    @Bean
    CategoryService categoryService(CategoryStore store, product.application.ProductStore products,
            OutboxWriter outbox, product.application.CategorySerializer serializer) {
        return new CategoryService(store, products, outbox, serializer);
    }

    @Bean
    product.application.StoreService storeService(product.application.Stores stores) {
        return new product.application.StoreService(stores);
    }

    @Bean
    product.application.MonolithProjectionService monolithProjectionService(
            product.adapters.rest.persistence.JdbcMonolithProductProjectionSink sink) {
        return new product.application.MonolithProjectionService(sink);
    }
}
