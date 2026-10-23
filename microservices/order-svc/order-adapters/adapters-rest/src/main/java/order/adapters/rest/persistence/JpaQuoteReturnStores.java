package order.adapters.rest.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import order.application.QuoteStore;
import order.application.ReturnStore;
import order.domain.Quote;
import order.domain.ReturnOrder;

/** JPA adapters for the Quote/Return ports (repositories are top-level —
 *  Spring Data does not scan nested repository interfaces). */
public final class JpaQuoteReturnStores {

    @Repository
    public static class JpaQuoteStore implements QuoteStore {

        private final QuoteRepository quotes;

        public JpaQuoteStore(QuoteRepository quotes) {
            this.quotes = quotes;
        }

        @Override
        public Optional<Quote> find(String quoteId) {
            return quotes.findById(quoteId).map(entity -> Quote.rehydrate(entity.getQuoteId(),
                entity.getCustomerId(), OrderJson.deserializeLines(entity.getLines()),
                Quote.Status.valueOf(entity.getStatus())));
        }

        @Override
        public void save(Quote quote) {
            quotes.save(new QuoteEntity(quote.quoteId(), quote.customerId(),
                quote.status().name(), OrderJson.serializeLines(quote.lines())));
        }
    }

    @Repository
    public static class JpaReturnStore implements ReturnStore {

        private final ReturnRepository returns;

        public JpaReturnStore(ReturnRepository returns) {
            this.returns = returns;
        }

        @Override
        public Optional<ReturnOrder> find(String returnId) {
            return returns.findById(returnId).map(entity -> ReturnOrder.rehydrate(
                entity.getReturnId(), entity.getOrderId(),
                OrderJson.deserializeReturnLines(entity.getLines()),
                ReturnOrder.Status.valueOf(entity.getStatus())));
        }

        @Override
        public void save(ReturnOrder returnOrder) {
            returns.save(new ReturnEntity(returnOrder.returnId(), returnOrder.orderId(),
                returnOrder.status().name(),
                OrderJson.serializeReturnLines(returnOrder.lines())));
        }
    }

    private JpaQuoteReturnStores() {
    }
}
