package product.adapters.rest.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import product.application.StoreService;
import product.domain.Store;

/** North-south REST edge (ADR-010): only the gateway routes here. */
@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    public record UpsertStoreRequest(String productStoreId, String storeName,
            String defaultCurrencyUomId, String inventoryFacilityId) {
    }

    public record StoreResponse(String productStoreId, String storeName,
            String defaultCurrencyUomId, String inventoryFacilityId) {
    }

    /** Store settings read/write (P3-F1-S3 AC). */
    @PostMapping
    ResponseEntity<StoreResponse> upsert(@RequestBody UpsertStoreRequest request) {
        Store store;
        try {
            store = new Store(request.productStoreId(), request.storeName(),
                request.defaultCurrencyUomId(), request.inventoryFacilityId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
        storeService.upsert(store);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(store));
    }

    /** P9-F2: store listing for the SPA catalog area. */
    @GetMapping
    java.util.List<StoreResponse> list() {
        return storeService.all().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{productStoreId}")
    ResponseEntity<StoreResponse> get(@PathVariable String productStoreId) {
        return storeService.get(productStoreId)
            .map(this::toResponse)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Delete (spec-review fix): no event, consistent with store upserts. */
    @DeleteMapping("/{productStoreId}")
    ResponseEntity<Void> delete(@PathVariable String productStoreId) {
        if (!storeService.delete(productStoreId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    private StoreResponse toResponse(Store store) {
        return new StoreResponse(store.productStoreId(), store.storeName(),
            store.defaultCurrencyUomId(), store.inventoryFacilityId());
    }
}
