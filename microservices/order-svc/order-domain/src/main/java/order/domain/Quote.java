package order.domain;

import java.util.List;

/**
 * Quote aggregate (P4-F1-S4): a draft of snapshot-priced lines that
 * converts into an order without losing or re-pricing any line data.
 */
public final class Quote {

    public enum Status { DRAFT, CONVERTED }

    private final String quoteId;
    private final String customerId;
    private final List<OrderLine> lines;
    private Status status = Status.DRAFT;

    public Quote(String quoteId, String customerId, List<OrderLine> lines) {
        if (quoteId == null || quoteId.isBlank()) {
            throw new IllegalArgumentException("quoteId must not be blank");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be blank");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("a quote needs at least one line");
        }
        this.quoteId = quoteId;
        this.customerId = customerId;
        this.lines = List.copyOf(lines);
    }

    /**
     * P4-F1-S4 AC: quote→order conversion preserves line data — the exact
     * same OrderLine instances (seq ids, prices, facilities) move over.
     *
     * @throws IllegalStateException when the quote was already converted.
     */
    public List<OrderLine> convert() {
        if (status != Status.DRAFT) {
            throw new IllegalStateException("quote " + quoteId + " is already " + status);
        }
        status = Status.CONVERTED;
        return lines;
    }

    public static Quote rehydrate(String quoteId, String customerId, List<OrderLine> lines,
            Status status) {
        Quote quote = new Quote(quoteId, customerId, lines);
        quote.status = status;
        return quote;
    }

    public String quoteId() {
        return quoteId;
    }

    public String customerId() {
        return customerId;
    }

    public List<OrderLine> lines() {
        return lines;
    }

    public Status status() {
        return status;
    }
}
