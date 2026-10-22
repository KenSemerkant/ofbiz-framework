package inventory.adapters.rest.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
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
import inventory.application.ShipmentService;
import inventory.domain.InsufficientStockException;
import inventory.domain.InvalidTransitionException;
import inventory.domain.Shipment;

/**
 * North-south REST edge (ADR-010): only the gateway routes here.
 * Invalid status transitions are business conflicts → 409 (P3-F2-S3 AC).
 */
@RestController
@RequestMapping("/api/v1/shipments")
public class ShipmentsController {

    private final ShipmentOperations operations;
    private final ShipmentService shipmentService;

    public ShipmentsController(ShipmentOperations operations, ShipmentService shipmentService) {
        this.operations = operations;
        this.shipmentService = shipmentService;
    }

    public record ItemRequest(String productId, BigDecimal quantity, String inventoryItemId) {
    }

    public record CreateShipmentRequest(String shipmentId, String orderId,
            List<ItemRequest> items) {
    }

    public record ItemResponse(String shipmentItemSeqId, String productId, BigDecimal quantity,
            String inventoryItemId) {
    }

    public record ShipmentResponse(String shipmentId, String orderId, String status,
            List<ItemResponse> items) {
    }

    @PostMapping
    ResponseEntity<ShipmentResponse> create(@RequestBody CreateShipmentRequest request) {
        try {
            Shipment shipment = new Shipment(request.shipmentId(), request.orderId());
            if (request.items() != null) {
                for (ItemRequest item : request.items()) {
                    shipment.addShipmentItem(item.productId(), item.quantity(),
                        item.inventoryItemId());
                }
            }
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(OptimisticRetry.run(() -> operations.create(shipment))));
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest().body(null);
        } catch (DuplicateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        }
    }

    @GetMapping("/{shipmentId}")
    ResponseEntity<ShipmentResponse> get(@PathVariable String shipmentId) {
        return shipmentService.get(shipmentId)
            .map(this::toResponse)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{shipmentId}/pack")
    ResponseEntity<ShipmentResponse> pack(@PathVariable String shipmentId) {
        try {
            return ResponseEntity.ok(
                toResponse(OptimisticRetry.run(() -> operations.pack(shipmentId))));
        } catch (InvalidTransitionException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** P3-F2-S3 ship-order flow: transitions to SHIPPED, confirms linked reservations. */
    @PostMapping("/{shipmentId}/ship")
    ResponseEntity<ShipmentResponse> ship(@PathVariable String shipmentId) {
        try {
            return ResponseEntity.ok(
                toResponse(OptimisticRetry.run(() -> operations.ship(shipmentId))));
        } catch (InvalidTransitionException | InsufficientStockException
                | OptimisticLockingFailureException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private ShipmentResponse toResponse(Shipment shipment) {
        return new ShipmentResponse(shipment.shipmentId(), shipment.orderId(),
            shipment.status().name(),
            shipment.items().stream()
                .map(i -> new ItemResponse(i.shipmentItemSeqId(), i.productId(), i.quantity(),
                    i.inventoryItemId()))
                .toList());
    }
}
