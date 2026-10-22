package inventory.adapters.rest.api;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import inventory.application.DuplicateException;
import inventory.application.InventoryService;
import inventory.domain.InsufficientStockException;
import inventory.domain.InventoryItem;

/**
 * North-south REST edge (ADR-010): only the gateway routes here.
 * Stock ops retry the whole transaction on optimistic-lock conflict
 * (quality review: same 409-instead-of-500 treatment as reservations).
 * IllegalArgumentException → 400 (malformed), business conflicts
 * (DuplicateException / InsufficientStockException / optimistic lock /
 * duplicate-id race) → 409, NoSuchElementException → 404; anything else
 * (infra, e.g. outbox down) surfaces as 500 so failures are never mistaken
 * for conflicts.
 */
@RestController
@RequestMapping("/api/v1/inventory-items")
public class InventoryItemController {

    private final InventoryService inventoryService;
    private final ItemOperations operations;

    public InventoryItemController(InventoryService inventoryService,
            ItemOperations operations) {
        this.inventoryService = inventoryService;
        this.operations = operations;
    }

    public record CreateItemRequest(String inventoryItemId, String productId, String facilityId,
            String serialNumber, BigDecimal unitCost) {
    }

    public record QuantityRequest(BigDecimal quantity) {
    }

    public record TransferRequest(String toInventoryItemId, BigDecimal quantity) {
    }

    public record ItemResponse(String inventoryItemId, String productId, String facilityId,
            String serialNumber, BigDecimal unitCost, BigDecimal quantityOnHandTotal,
            BigDecimal availableToPromiseTotal) {
    }

    @PostMapping
    ResponseEntity<ItemResponse> create(@RequestBody CreateItemRequest request) {
        InventoryItem item;
        try {
            item = new InventoryItem(request.inventoryItemId(), request.productId(),
                request.facilityId(), request.serialNumber(), request.unitCost());
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest().body(null);
        }
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(operations.create(item)));
        } catch (DuplicateException | DataIntegrityViolationException e) {
            // DataIntegrityViolation: duplicate-id create race lost (PK hit)
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        }
    }

    @GetMapping("/{inventoryItemId}")
    ResponseEntity<ItemResponse> get(@PathVariable String inventoryItemId) {
        return inventoryService.get(inventoryItemId)
            .map(this::toResponse)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** P3-F2-S1 receive: stock in, QOH + ATP rise. */
    @PostMapping("/{inventoryItemId}/receive")
    ResponseEntity<ItemResponse> receive(@PathVariable String inventoryItemId,
            @RequestBody QuantityRequest request) {
        try {
            return ResponseEntity.ok(toResponse(OptimisticRetry.run(
                () -> operations.receive(inventoryItemId, request.quantity()))));
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest().body(null);
        } catch (InsufficientStockException | OptimisticLockingFailureException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** P3-F2-S1 adjust: variance ±, both totals move together. */
    @PostMapping("/{inventoryItemId}/adjust")
    ResponseEntity<ItemResponse> adjust(@PathVariable String inventoryItemId,
            @RequestBody QuantityRequest request) {
        try {
            return ResponseEntity.ok(toResponse(OptimisticRetry.run(
                () -> operations.adjust(inventoryItemId, request.quantity()))));
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest().body(null);
        } catch (InsufficientStockException | OptimisticLockingFailureException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** P3-F2-S1 transfer: move qty between two items; source ATP must cover it. */
    @PostMapping("/{inventoryItemId}/transfer")
    ResponseEntity<ItemResponse> transfer(@PathVariable String inventoryItemId,
            @RequestBody TransferRequest request) {
        try {
            return ResponseEntity.ok(toResponse(OptimisticRetry.run(
                () -> operations.transfer(inventoryItemId, request.toInventoryItemId(),
                    request.quantity()))));
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest().body(null);
        } catch (InsufficientStockException | OptimisticLockingFailureException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private ItemResponse toResponse(InventoryItem item) {
        return new ItemResponse(item.inventoryItemId(), item.productId(), item.facilityId(),
            item.serialNumber(), item.unitCost(), item.quantityOnHandTotal(),
            item.availableToPromiseTotal());
    }
}
