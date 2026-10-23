package order.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** P4-F1-S4 quote conversion + P4-F1-S3 return guards. */
class QuoteReturnTest {

    private static final List<OrderLine> LINES = List.of(
        new OrderLine("00001", "PROD-1", "WH", BigDecimal.ONE, new BigDecimal("9.99"), "USD"));

    @Test
    void conversionPreservesLineDataExactly() {
        Quote quote = new Quote("QUOTE-1", "CUST-1", LINES);
        List<OrderLine> converted = quote.convert();
        assertSame(LINES.get(0), converted.get(0), "the very same line instance moves over");
        assertEquals(Quote.Status.CONVERTED, quote.status());
        assertThrows(IllegalStateException.class, quote::convert,
                "a converted quote cannot convert twice");
    }

    @Test
    void rehydrateTrustsPersistedState() {
        Quote quote = Quote.rehydrate("QUOTE-2", "CUST-1", LINES, Quote.Status.CONVERTED);
        assertEquals(Quote.Status.CONVERTED, quote.status());
        assertEquals("QUOTE-2", quote.quoteId());
        assertEquals("CUST-1", quote.customerId());
        assertEquals(LINES, quote.lines());
        ReturnOrder returns = ReturnOrder.rehydrate("RET-1", "ORDER-1",
            List.of(new ReturnLine("PROD-1", "WH", BigDecimal.ONE)),
            ReturnOrder.Status.COMPLETED);
        assertEquals(ReturnOrder.Status.COMPLETED, returns.status());
    }

    @Test
    void returnCompletionIsGuarded() {
        ReturnOrder returns = new ReturnOrder("RET-2", "ORDER-1",
            List.of(new ReturnLine("PROD-1", "WH", BigDecimal.ONE)));
        returns.complete();
        assertThrows(IllegalStateException.class, returns::complete);
    }

    @Test
    void constructorGuards() {
        assertThrows(IllegalArgumentException.class,
            () -> new Quote(" ", "CUST-1", LINES));
        assertThrows(IllegalArgumentException.class,
            () -> new Quote("QUOTE-3", " ", LINES));
        assertThrows(IllegalArgumentException.class,
            () -> new Quote("QUOTE-4", "CUST-1", List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new ReturnOrder(null, "ORDER-1",
                List.of(new ReturnLine("PROD-1", "WH", BigDecimal.ONE))));
        assertThrows(IllegalArgumentException.class,
            () -> new ReturnOrder("RET-3", "ORDER-1", List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new ReturnLine("PROD-1", "WH", BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
            () -> new ReturnLine("PROD-1", null, BigDecimal.ONE));
    }
}
