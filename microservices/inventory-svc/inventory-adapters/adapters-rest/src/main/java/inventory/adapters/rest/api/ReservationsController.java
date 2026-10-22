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
 * bounded (P3-F2-S2 AC): 3 attempts, then 409.
 */
@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationsController {

    /** Bounded optimistic-lock retry (P3-F2-S2 AC). */
    static final int MAX_ATTEMPTS = 3;

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
            Reservation reservation = withRetry(() -> operations.reserve(request.orderId(),
                request.orderItemSeqId(), request.productId(), request.facilityId(),
                request.quantity()));
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(reservation));
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest().body(null);
        } catch (InsufficientStockException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (OptimisticLockingFailureException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        }
    }

    @PostMapping("/{reservationId}/confirm")
    ResponseEntity<ReservationResponse> confirm(@PathVariable String reservationId) {
        try {
            return ResponseEntity.status(HttpStatus.OK)
                .body(toResponse(withRetry(() -> operations.confirm(reservationId))));
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
                .body(toResponse(withRetry(() -> operations.cancel(reservationId))));
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

    /**
     * Runs op with the bounded optimistic-lock retry; rethrows when exhausted.
     * Immediate retries cannot drain a thundering herd (measured: 11-12 of 50
     * wins under 100 parallel reserves) — every collision round crowns exactly
     * one winner — so attempts back off with jitter to spread the herd over
     * the ~100ms it takes for ATP-worth of winners to commit. The sleeping
     * thread holds no transaction/connection between attempts.
     */
    private Reservation withRetry(java.util.function.Supplier<Reservation> op) {
        OptimisticLockingFailureException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return op.get();
            } catch (OptimisticLockingFailureException e) {
                last = e;
                if (attempt < MAX_ATTEMPTS) {
                    backoff(attempt);
                }
            }
        }
        throw last;
    }

    private static void backoff(int attempt) {
        long base = switch (attempt) {
            case 1 -> 5; // 5-10ms
            case 2 -> 20; // 20-40ms
            default -> 0;
        };
        try {
            Thread.sleep(base
                + java.util.concurrent.ThreadLocalRandom.current().nextLong(base));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(reservation.reservationId(), reservation.orderId(),
            reservation.orderItemSeqId(), reservation.productId(), reservation.facilityId(),
            reservation.inventoryItemId(), reservation.quantity(), reservation.status().name());
    }
}
