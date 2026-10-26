package order.adapters.rest.api;

import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import order.application.OrderLifecycleService;
import order.application.PlaceOrderSaga;
import order.application.PriceUnavailableException;
import order.domain.IllegalOrderTransitionException;
import order.domain.Order;
import order.domain.OrderStatusChange;

/** North-south REST edge (ADR-010): only the gateway routes here. */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final PlaceOrderSaga placeOrder;
    private final OrderLifecycleService lifecycle;

    public OrderController(PlaceOrderSaga placeOrder, OrderLifecycleService lifecycle) {
        this.placeOrder = placeOrder;
        this.lifecycle = lifecycle;
    }

    public record CartLineDto(String productId, String facilityId,
            java.math.BigDecimal quantity) {
    }

    public record PlaceOrderRequest(String customerId, String currencyUomId,
            List<CartLineDto> lines, String trackingCode) {
    }

    public record LineResponse(String orderItemSeqId, String productId, String facilityId,
            java.math.BigDecimal quantity, java.math.BigDecimal unitPrice,
            String currencyUomId) {
    }

    public record OrderResponse(String orderId, String customerId, String trackingCode,
            String status, List<LineResponse> lines, java.math.BigDecimal grandTotal,
            String currencyUomId) {
    }

    public record HistoryResponse(String orderId, List<StatusDto> history) {
    }

    public record StatusDto(String status, String changedAt, String note) {
    }

    public record CancelRequest(String note) {
    }

    /** P4-F1-S2 cart → order; the adapter-layer tx covers order+saga+outbox. */
    @PostMapping
    @Transactional
    ResponseEntity<OrderResponse> place(@RequestBody PlaceOrderRequest request) {
        Order order;
        try {
            order = placeOrder.place(request.customerId(), request.currencyUomId(),
                request.lines() == null ? List.of()
                    : request.lines().stream()
                        .map(line -> new PlaceOrderSaga.CartLine(line.productId(),
                            line.facilityId(), line.quantity()))
                        .toList(),
                request.trackingCode());
        } catch (IllegalArgumentException | NullPointerException
                | order.application.PartyValidationException e) {
            return ResponseEntity.badRequest().body(null);
        } catch (PriceUnavailableException e) {
            return ResponseEntity.unprocessableEntity().body(null);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(order));
    }

    /** P9-F3: order search for the SPA order area (null filter = match all). */
    @GetMapping
    java.util.List<OrderResponse> search(@org.springframework.web.bind.annotation.RequestParam(
            required = false) String customerId,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            String status) {
        return lifecycle.search(customerId, status).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{orderId}")
    ResponseEntity<OrderResponse> get(@PathVariable String orderId) {
        return lifecycle.get(orderId).map(this::toResponse).map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** P4-F1-S1 AC: the append-only status history per order. */
    @GetMapping("/{orderId}/history")
    ResponseEntity<HistoryResponse> history(@PathVariable String orderId) {
        return lifecycle.get(orderId).map(order -> new HistoryResponse(orderId,
            order.history().stream()
                .map(OrderController::toStatusDto)
                .toList()))
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** @return 409 with the typed error when the transition is illegal. */
    @PostMapping("/{orderId}/approve")
    @Transactional
    ResponseEntity<OrderResponse> approve(@PathVariable String orderId) {
        return guarded(() -> toResponse(lifecycle.approve(orderId)));
    }

    @PostMapping("/{orderId}/complete")
    @Transactional
    ResponseEntity<OrderResponse> complete(@PathVariable String orderId) {
        return guarded(() -> toResponse(lifecycle.complete(orderId)));
    }

    @PostMapping("/{orderId}/cancel")
    @Transactional
    ResponseEntity<OrderResponse> cancel(@PathVariable String orderId,
            @RequestBody(required = false) CancelRequest request) {
        return guarded(() -> toResponse(
            lifecycle.cancel(orderId, request == null ? null : request.note())));
    }

    private interface SupplierThrowingNotFound<T> {
        T get();
    }

    private ResponseEntity<OrderResponse> guarded(SupplierThrowingNotFound<OrderResponse> call) {
        try {
            return ResponseEntity.ok(call.get());
        } catch (IllegalOrderTransitionException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private static StatusDto toStatusDto(OrderStatusChange change) {
        return new StatusDto(change.status().name(), change.changedAt().toString(),
            change.note());
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(order.orderId(), order.customerId(), order.trackingCode(),
            order.status().name(),
            order.lines().stream()
                .map(line -> new LineResponse(line.orderItemSeqId(), line.productId(),
                    line.facilityId(), line.quantity(), line.unitPrice(),
                    line.currencyUomId()))
                .toList(),
            order.grandTotal(), order.lines().get(0).currencyUomId());
    }
}
