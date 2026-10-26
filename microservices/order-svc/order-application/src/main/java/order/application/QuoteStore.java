package order.application;

import java.util.Optional;
import order.domain.Quote;

/** Persistence port for quotes. */
public interface QuoteStore {

    Optional<Quote> find(String quoteId);

    /** P9-F3: quote listing for the SPA order area. */
    java.util.List<Quote> all();

    void save(Quote quote);
}
