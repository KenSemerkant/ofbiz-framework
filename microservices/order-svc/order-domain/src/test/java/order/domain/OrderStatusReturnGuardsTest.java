package order.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Branch coverage for the status table and the remaining guards. */
class OrderStatusReturnGuardsTest {

    @Test
    void transitionTableAllowsOnlyTheLegalEdges() {
        assertTrue(OrderStatus.canTransition(OrderStatus.CREATED, OrderStatus.APPROVED));
        assertTrue(OrderStatus.canTransition(OrderStatus.CREATED, OrderStatus.CANCELLED));
        assertTrue(OrderStatus.canTransition(OrderStatus.APPROVED, OrderStatus.COMPLETED));
        assertTrue(OrderStatus.canTransition(OrderStatus.APPROVED, OrderStatus.CANCELLED));
        assertFalse(OrderStatus.canTransition(OrderStatus.CREATED, OrderStatus.COMPLETED));
        assertFalse(OrderStatus.canTransition(OrderStatus.CREATED, OrderStatus.CREATED));
        assertFalse(OrderStatus.canTransition(OrderStatus.COMPLETED, OrderStatus.CANCELLED));
        assertFalse(OrderStatus.canTransition(OrderStatus.CANCELLED, OrderStatus.APPROVED));
        OrderStatus.requireTransition(OrderStatus.CREATED, OrderStatus.APPROVED);
        assertThrows(IllegalOrderTransitionException.class,
            () -> OrderStatus.requireTransition(OrderStatus.COMPLETED, OrderStatus.CREATED));
    }

    @Test
    void typedErrorCarriesFromAndTo() {
        IllegalOrderTransitionException error =
            new IllegalOrderTransitionException(OrderStatus.CREATED, OrderStatus.COMPLETED);
        assertEquals(OrderStatus.CREATED, error.from());
        assertEquals(OrderStatus.COMPLETED, error.to());
        assertTrue(error.getMessage().contains("CREATED -> COMPLETED"));
    }

    @Test
    void returnOrderAccessorsAndGuards() {
        ReturnLine line = new ReturnLine("PROD-1", "WH", BigDecimal.ONE);
        assertEquals("PROD-1", line.productId());
        assertEquals("WH", line.facilityId());
        assertEquals(0, BigDecimal.ONE.compareTo(line.quantity()));
        assertThrows(IllegalArgumentException.class, () -> new ReturnLine("", "WH", BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
            () -> new ReturnLine("PROD-1", "WH", new BigDecimal("-1")));

        ReturnOrder returns = new ReturnOrder("RET-9", "ORDER-9", List.of(line));
        assertEquals("RET-9", returns.returnId());
        assertEquals("ORDER-9", returns.orderId());
        assertEquals(List.of(line), returns.lines());
        assertEquals(ReturnOrder.Status.RECEIVED, returns.status());
        assertThrows(IllegalArgumentException.class, () -> new ReturnOrder("RET", "", List.of(line)));
    }

    @Test
    void orderLineAccessors() {
        OrderLine line = new OrderLine("00001", "PROD-1", "WH", new BigDecimal("3"),
            new BigDecimal("2.50"), "EUR");
        assertEquals("00001", line.orderItemSeqId());
        assertEquals("PROD-1", line.productId());
        assertEquals("WH", line.facilityId());
        assertEquals("EUR", line.currencyUomId());
        assertEquals(0, new BigDecimal("7.50").compareTo(line.lineTotal()));
        assertThrows(IllegalArgumentException.class,
            () -> new OrderLine(null, "PROD-1", "WH", BigDecimal.ONE, BigDecimal.TEN, "USD"));
        assertThrows(IllegalArgumentException.class,
            () -> new OrderLine("00001", " ", "WH", BigDecimal.ONE, BigDecimal.TEN, "USD"));
        assertThrows(IllegalArgumentException.class,
            () -> new OrderLine("00001", "PROD-1", " ", BigDecimal.ONE, BigDecimal.TEN, "USD"));
        assertThrows(IllegalArgumentException.class,
            () -> new OrderLine("00001", "PROD-1", "WH", BigDecimal.ZERO, BigDecimal.TEN, "USD"));
        assertThrows(IllegalArgumentException.class,
            () -> new OrderLine("00001", "PROD-1", "WH", BigDecimal.ONE,
                    new BigDecimal("-1"), "USD"));
        assertThrows(IllegalArgumentException.class,
            () -> new OrderLine("00001", "PROD-1", "WH", BigDecimal.ONE, BigDecimal.TEN, " "));
    }
}
