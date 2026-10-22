package product.adapters.rest.api;

import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import product.adapters.rest.persistence.ProductJson;
import product.application.ProductService;
import product.domain.Product;
import product.domain.ProductAssoc;

/** North-south REST edge (ADR-010): only the gateway routes here. */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    public record UpsertProductRequest(String productId, String productTypeId,
            String internalName, String productName, Set<String> categoryIds,
            List<ProductJson.AssocDto> associations) {
    }

    public record ProductResponse(String productId, String productTypeId, String internalName,
            String productName, Set<String> categoryIds, List<ProductJson.AssocDto> associations) {
    }

    @PostMapping
    ResponseEntity<ProductResponse> upsert(@RequestBody UpsertProductRequest request) {
        Product product;
        try {
            product = new Product(request.productId(), request.productTypeId(),
                request.internalName(), request.productName(), request.categoryIds(), null);
            if (request.associations() != null) {
                for (ProductJson.AssocDto assoc : request.associations()) {
                    product.associateWith(new ProductAssoc(assoc.productIdTo(),
                        assoc.productAssocTypeId(), assoc.fromDate(), assoc.thruDate()));
                }
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest().body(null);
        }
        productService.upsert(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(product));
    }

    @GetMapping("/{productId}")
    ResponseEntity<ProductResponse> get(@PathVariable String productId) {
        return productService.get(productId)
            .map(this::toResponse)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(product.productId(), product.productTypeId(),
            product.internalName(), product.productName(), product.categoryIds(),
            product.associations().stream()
                .map(a -> new ProductJson.AssocDto(a.productIdTo(), a.productAssocTypeId(),
                    a.fromDate(), a.thruDate()))
                .toList());
    }
}
