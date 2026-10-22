package inventory.adapters.rest.api;

import java.math.BigDecimal;
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
import inventory.domain.InsufficientStockException;
import inventory.domain.InvalidTransitionException;
import inventory.domain.Reservation;

/**
 * North-south REST edge (ADR-010): only the gateway routes here.
 * Each op retries the whole transaction on optimistic-lock conflict —
 * bounded (P3-F2-S2 AC): 3 attempts, then 409 (see OptimisticRetry).
 */
@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationsController {

    private final ReservationOperations operations;
    private final inventory.application.ReservationService reservationService;

    public ReservationsController(ReservationOperations operations,
            inventory.application.ReservationService reservationService) {
        this.operations = operations;
        this.reservationService = reservationService;
    }

    public record ReserveRequest(String orderId, String orderItemSeqId, String productId,
            String facilityId, BigDecimal quantity) {
    }

    public record ReservationResponse(String reservationId, String orderId, String orderItemSeqId,
            String productId, String facilityId, String inventoryItemId, BigDecimal quantity,
            String status) {
    }

    @PostMapping
    ResponseEntity<ReservationResponse> reserve(@RequestBody ReserveRequest request) {
        try {
            Reservation reservation = OptimisticRetry.run(() -> operations.reserve(
                request.orderId(), request.orderItemSeqId(), request.productId(),
                request.facilityId(), request.quantity()));
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(reservation));
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest().body(null);
        } catch (InsufficientStockException | OptimisticLockingFailureException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{reservationId}/confirm")
    ResponseEntity<ReservationResponse> confirm(@PathVariable String reservationId) {
        try {
            return ResponseEntity.status(HttpStatus.OK)
                .body(toResponse(OptimisticRetry.run(() -> operations.confirm(reservationId))));
        } catch (InsufficientStockException | InvalidTransitionException
                | OptimisticLockingFailureException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{reservationId}/cancel")
    ResponseEntity<ReservationResponse> cancel(@PathVariable String reservationId) {
        try {
            return ResponseEntity.status(HttpStatus.OK)
                .body(toResponse(OptimisticRetry.run(() -> operations.cancel(reservationId))));
        } catch (InsufficientStockException | InvalidTransitionException
                | OptimisticLockingFailureException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{reservationId}")
    ResponseEntity<ReservationResponse> get(@PathVariable String reservationId) {
        return reservationService.get(reservationId)
            .map(this::toResponse)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(reservation.reservationId(), reservation.orderId(),
            reservation.orderItemSeqId(), reservation.productId(), reservation.facilityId(),
            reservation.inventoryItemId(), reservation.quantity(), reservation.status().name());
    }
}
