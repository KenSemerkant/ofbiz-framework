package order.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import order.domain.Order;
import order.domain.OrderLine;
import order.domain.Quote;

/**
 * P4-F1-S4: quote creation with snapshot pricing, then conversion to an
 * order through the same placeOrder saga — conversion preserves the quote's
 * line data exactly (prices captured at quote time, no re-pricing).
 */
public class QuoteService {

    private final QuoteStore quotes;
    private final PlaceOrderSaga placeOrder;

    public QuoteService(QuoteStore quotes, PlaceOrderSaga placeOrder) {
        this.quotes = quotes;
        this.placeOrder = placeOrder;
    }

    public record QuoteLine(String productId, String facilityId, BigDecimal quantity) {
    }

    /** P9-F3: quote listing for the SPA order area. */
    public java.util.List<Quote> all() {
        return quotes.all().stream()
            .sorted(java.util.Comparator.comparing(Quote::quoteId)).toList();
    }

    public Optional<Quote> get(String quoteId) {
        return quotes.find(quoteId);
    }

    /**
     * @throws PartyValidationException  unknown party
     * @throws PriceUnavailableException a line has no applicable price
     */
    public Quote create(String customerId, String currencyUomId, List<QuoteLine> lines) {
        if (!placeOrder.partyValidator().isValid(customerId)) {
            throw new PartyValidationException(customerId);
        }
        List<OrderLine> snapshot = placeOrder.snapshotPrices(lines.stream()
            .map(line -> new PlaceOrderSaga.CartLine(line.productId(), line.facilityId(),
                line.quantity()))
            .toList(), currencyUomId);
        Quote quote = new Quote("QUOTE-" + UUID.randomUUID(), customerId, snapshot);
        quotes.save(quote);
        return quote;
    }

    /**
     * P4-F1-S4 AC: conversion preserves line data — the snapshot lines move
     * into the order verbatim.
     *
     * @throws IllegalStateException the quote was already converted.
     */
    public Order convert(String quoteId) {
        Quote quote = quotes.find(quoteId)
            .orElseThrow(() -> new NoSuchElementException("no quote " + quoteId));
        List<OrderLine> lines = quote.convert();
        quotes.save(quote);
        return placeOrder.placeWithLines(quote.customerId(), lines, null);
    }
}
