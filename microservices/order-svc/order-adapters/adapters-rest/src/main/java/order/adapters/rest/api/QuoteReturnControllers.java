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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import order.application.AttributionService;
import order.application.QuoteService;
import order.application.ReturnService;
import order.domain.ReturnLine;
import order.domain.ReturnOrder;

/** Quote conversion (P4-F1-S4), returns (P4-F1-S3), attribution read (P4-F2-S2). */
public final class QuoteReturnControllers {

    @RestController
    @RequestMapping("/api/v1/quotes")
    public static class QuoteController {

        private final QuoteService quotes;

        public QuoteController(QuoteService quotes) {
            this.quotes = quotes;
        }

        public record QuoteLineDto(String productId, String facilityId,
                java.math.BigDecimal quantity) {
        }

        public record CreateQuoteRequest(String customerId, String currencyUomId,
                List<QuoteLineDto> lines) {
        }

        public record QuoteResponse(String quoteId, String customerId, String status,
                List<OrderController.LineResponse> lines) {
        }

        @PostMapping
        @Transactional
        ResponseEntity<QuoteResponse> create(@RequestBody CreateQuoteRequest request) {
            try {
                var quote = quotes.create(request.customerId(), request.currencyUomId(),
                    request.lines().stream()
                        .map(line -> new QuoteService.QuoteLine(line.productId(),
                            line.facilityId(), line.quantity()))
                        .toList());
                return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(quote));
            } catch (IllegalArgumentException | NullPointerException
                    | order.application.PartyValidationException
                    | order.application.PriceUnavailableException e) {
                return ResponseEntity.badRequest().body(null);
            }
        }

        /** P4-F1-S4 AC: conversion preserves line data. */
        @PostMapping("/{quoteId}/convert")
        @Transactional
        ResponseEntity<OrderController.OrderResponse> convert(@PathVariable String quoteId) {
            try {
                var order = quotes.convert(quoteId);
                return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new OrderController.OrderResponse(order.orderId(),
                        order.customerId(), order.trackingCode(), order.status().name(),
                        order.lines().stream()
                            .map(line -> new OrderController.LineResponse(line.orderItemSeqId(),
                                line.productId(), line.facilityId(), line.quantity(),
                                line.unitPrice(), line.currencyUomId()))
                            .toList(),
                        order.grandTotal(), order.lines().get(0).currencyUomId()));
            } catch (IllegalStateException e) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
            } catch (NoSuchElementException e) {
                return ResponseEntity.notFound().build();
            }
        }

        @GetMapping("/{quoteId}")
        ResponseEntity<QuoteResponse> get(@PathVariable String quoteId) {
            return quotes.get(quoteId).map(this::toResponse).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
        }

        private QuoteResponse toResponse(order.domain.Quote quote) {
            return new QuoteResponse(quote.quoteId(), quote.customerId(), quote.status().name(),
                quote.lines().stream()
                    .map(line -> new OrderController.LineResponse(line.orderItemSeqId(),
                        line.productId(), line.facilityId(), line.quantity(), line.unitPrice(),
                        line.currencyUomId()))
                    .toList());
        }
    }

    @RestController
    @RequestMapping("/api/v1/returns")
    public static class ReturnController {

        private final ReturnService returns;

        public ReturnController(ReturnService returns) {
            this.returns = returns;
        }

        public record ReturnLineDto(String productId, String facilityId,
                java.math.BigDecimal quantity) {
        }

        public record CreateReturnRequest(String orderId, List<ReturnLineDto> lines) {
        }

        public record ReturnResponse(String returnId, String orderId, String status,
                List<ReturnLineDto> lines) {
        }

        /** P4-F1-S3 AC: creation adjusts inventory + emits the credit memo. */
        @PostMapping
        @Transactional
        ResponseEntity<ReturnResponse> create(@RequestBody CreateReturnRequest request) {
            try {
                ReturnOrder created = returns.create(request.orderId(),
                    request.lines().stream()
                        .map(line -> new ReturnLine(line.productId(), line.facilityId(),
                            line.quantity()))
                        .toList());
                return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
            } catch (IllegalStateException e) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
            } catch (NoSuchElementException e) {
                return ResponseEntity.notFound().build();
            }
        }

        @GetMapping("/{returnId}")
        ResponseEntity<ReturnResponse> get(@PathVariable String returnId) {
            return returns.get(returnId).map(this::toResponse).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
        }

        private ReturnResponse toResponse(ReturnOrder returnOrder) {
            return new ReturnResponse(returnOrder.returnId(), returnOrder.orderId(),
                returnOrder.status().name(),
                returnOrder.lines().stream()
                    .map(line -> new ReturnLineDto(line.productId(), line.facilityId(),
                        line.quantity()))
                    .toList());
        }
    }

    @RestController
    @RequestMapping("/api/v1/attributions")
    public static class AttributionController {

        private final AttributionService attributions;

        public AttributionController(AttributionService attributions) {
            this.attributions = attributions;
        }

        /** P4-F2-S2 AC evidence: attribution count per tracking code. */
        @GetMapping
        long count(@RequestParam String trackingCode) {
            return attributions.countByTrackingCode(trackingCode);
        }
    }

    private QuoteReturnControllers() {
    }
}
