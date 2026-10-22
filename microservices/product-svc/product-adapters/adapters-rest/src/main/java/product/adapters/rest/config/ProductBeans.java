package product.adapters.rest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
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
    ProductService productService(ProductStore store, OutboxWriter outbox,
            ProductSerializer serializer) {
        return new ProductService(store, outbox, serializer);
    }

    @Bean
    CategoryService categoryService(CategoryStore store, OutboxWriter outbox) {
        return new CategoryService(store, outbox);
    }
}
