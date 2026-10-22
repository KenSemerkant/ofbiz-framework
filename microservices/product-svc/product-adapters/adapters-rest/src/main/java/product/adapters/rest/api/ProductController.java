package product.adapters.rest.api;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;
import product.adapters.rest.persistence.ProductJson;
import product.application.ProductService;
import product.domain.Product;
import product.domain.ProductAssoc;
import product.domain.ProductPrice;

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
            List<ProductJson.AssocDto> associations, List<ProductJson.PriceDto> prices) {
    }

    public record ProductResponse(String productId, String productTypeId, String internalName,
            String productName, Set<String> categoryIds, List<ProductJson.AssocDto> associations,
            List<ProductJson.PriceDto> prices) {
    }

    public record PriceSnapshotResponse(String productId, String productPriceTypeId,
            String currencyUomId, java.math.BigDecimal price, Instant resolvedAt) {
    }

    /**
     * Adapter-layer transaction boundary (ADR-009, spec-review fix): the
     * state change and its outbox row(s) commit — or roll back — together.
     */
    @PostMapping
    @Transactional
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
            if (request.prices() != null) {
                for (ProductJson.PriceDto price : request.prices()) {
                    product.addPrice(new ProductPrice(price.productPriceTypeId(),
                        price.currencyUomId(), price.fromDate(), price.thruDate(), price.price()));
                }
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest().body(null);
        }
        productService.upsert(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(product));
    }

    /** AC (P3-F1-S2): applicable price at an instant; 404 when none applies. */
    @GetMapping("/{productId}/price")
    ResponseEntity<PriceSnapshotResponse> price(@PathVariable String productId,
            @RequestParam(defaultValue = "DEFAULT_PRICE") String priceType,
            @RequestParam String currency, @RequestParam Instant at) {
        return productService.resolvePrice(productId, priceType, currency, at)
            .map(snapshot -> new PriceSnapshotResponse(snapshot.productId(),
                snapshot.productPriceTypeId(), snapshot.currencyUomId(), snapshot.price(),
                snapshot.resolvedAt()))
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Delete (spec-review fix): row removal + deleted event commit together. */
    @DeleteMapping("/{productId}")
    @Transactional
    ResponseEntity<Void> delete(@PathVariable String productId) {
        if (!productService.delete(productId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
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
                .toList(),
            product.prices().stream()
                .map(p -> new ProductJson.PriceDto(p.productPriceTypeId(), p.currencyUomId(),
                    p.fromDate(), p.thruDate(), p.price()))
                .toList());
    }
}
