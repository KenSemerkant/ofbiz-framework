package order.application;

import java.util.Optional;
import order.domain.Quote;

/** Persistence port for quotes. */
public interface QuoteStore {

    Optional<Quote> find(String quoteId);

    void save(Quote quote);
}
