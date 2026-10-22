package inventory.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * P3-F2-S1 aggregate contract: ATP/QOH move together on receive/adjust, and
 * transfer refuses to overdraw the source. Why it matters: ATP is what
 * reservations draw on; letting it drift from QOH oversells stock.
 */
class InventoryItemTest {

    private static final BigDecimal TEN = new BigDecimal("10");

    private InventoryItem item() {
        return new InventoryItem("II1", "WIDGET", "WH1", null, null);
    }

    @Test
    void receiveRaisesBothTotals() {
        InventoryItem item = item();
        item.receive(TEN);
        assertEquals(0, item.quantityOnHandTotal().compareTo(TEN));
        assertEquals(0, item.availableToPromiseTotal().compareTo(TEN));
    }

    @Test
    void adjustMovesBothTotalsInEitherDirection() {
        InventoryItem item = item();
        item.receive(TEN);
        item.adjust(new BigDecimal("-3"));
        assertEquals(0, item.quantityOnHandTotal().compareTo(new BigDecimal("7")));
        assertEquals(0, item.availableToPromiseTotal().compareTo(new BigDecimal("7")));
        item.adjust(new BigDecimal("2"));
        assertEquals(0, item.availableToPromiseTotal().compareTo(new BigDecimal("9")));
    }

    @Test
    void adjustRejectsZeroAndNegativeTotals() {
        InventoryItem item = item();
        item.receive(TEN);
        assertThrows(IllegalArgumentException.class, () -> item.adjust(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> item.adjust(null));
        assertThrows(IllegalStateException.class,
            () -> item.adjust(new BigDecimal("-11")));
    }

    @Test
    void transferMovesStockAndRejectsInsufficientAtp() {
        InventoryItem from = item();
        from.receive(TEN);
        InventoryItem to = new InventoryItem("II2", "WIDGET", "WH2", null, null);

        from.transferTo(to, new BigDecimal("4"));
        assertEquals(0, from.quantityOnHandTotal().compareTo(new BigDecimal("6")));
        assertEquals(0, from.availableToPromiseTotal().compareTo(new BigDecimal("6")));
        assertEquals(0, to.quantityOnHandTotal().compareTo(new BigDecimal("4")));
        assertEquals(0, to.availableToPromiseTotal().compareTo(new BigDecimal("4")));

        assertThrows(IllegalStateException.class,
            () -> from.transferTo(to, new BigDecimal("7")));
    }

    @Test
    void transferGuards() {
        InventoryItem from = item();
        from.receive(TEN);
        assertThrows(IllegalArgumentException.class,
            () -> from.transferTo(new InventoryItem("II2", "WIDGET", "WH2", null, null), null));
        assertThrows(IllegalArgumentException.class,
            () -> from.transferTo(new InventoryItem("II2", "WIDGET", "WH2", null, null),
                BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> from.transferTo(null, TEN));
        assertThrows(IllegalArgumentException.class, () -> from.transferTo(from, TEN));
    }

    @Test
    void receiveRejectsNonPositiveQuantity() {
        assertThrows(IllegalArgumentException.class, () -> item().receive(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
            () -> item().receive(new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> item().receive(null));
    }

    @Test
    void identityGuards() {
        assertThrows(IllegalArgumentException.class,
            () -> new InventoryItem(" ", "WIDGET", "WH1", null, null));
        assertThrows(IllegalArgumentException.class,
            () -> new InventoryItem("II1", null, "WH1", null, null));
        assertThrows(IllegalArgumentException.class,
            () -> new InventoryItem("II1", "WIDGET", " ", null, null));
        assertThrows(IllegalArgumentException.class,
            () -> new InventoryItem("II1", "WIDGET", "WH1", " ", null));
    }

    @Test
    void rehydrationRejectsNegativeTotals() {
        assertThrows(IllegalArgumentException.class, () -> new InventoryItem("II1", "P", "F",
            null, null, new BigDecimal("-1"), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new InventoryItem("II1", "P", "F",
            null, null, BigDecimal.ZERO, new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> new InventoryItem("II1", "P", "F",
            null, null, null, BigDecimal.ZERO));
    }

    @Test
    void totalsDefaultToZero() {
        InventoryItem item = item();
        assertEquals(0, item.quantityOnHandTotal().signum());
        assertEquals(0, item.availableToPromiseTotal().signum());
        assertEquals(null, item.serialNumber());
        assertEquals(null, item.unitCost());
        assertEquals("II1", item.inventoryItemId());
        assertEquals("WIDGET", item.productId());
        assertEquals("WH1", item.facilityId());
    }

    @Test
    void reserveDrawsDownAtpOnly() {
        InventoryItem item = item();
        item.receive(TEN);
        item.reserve(new BigDecimal("4"));
        assertEquals(0, item.quantityOnHandTotal().compareTo(TEN));
        assertEquals(0, item.availableToPromiseTotal().compareTo(new BigDecimal("6")));
        assertThrows(InsufficientStockException.class,
            () -> item.reserve(new BigDecimal("7")));
        // malformed reserve attempts leave the totals untouched
        assertThrows(IllegalArgumentException.class, () -> item.reserve(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> item.reserve(null));
        assertEquals(0, item.availableToPromiseTotal().compareTo(new BigDecimal("6")));
    }

    @Test
    void canReserveReflectsAtpOnly() {
        InventoryItem item = item();
        item.receive(TEN);
        assertEquals(true, item.canReserve(TEN));
        assertEquals(false, item.canReserve(new BigDecimal("11")));
        assertEquals(false, item.canReserve(BigDecimal.ZERO));
        assertEquals(false, item.canReserve(null));
        item.reserve(TEN);
        assertEquals(false, item.canReserve(BigDecimal.ONE));
    }

    @Test
    void confirmDrawsDownQohOnly() {
        InventoryItem item = item();
        item.receive(TEN);
        item.reserve(new BigDecimal("4"));
        item.confirmReservation(new BigDecimal("4"));
        assertEquals(0, item.quantityOnHandTotal().compareTo(new BigDecimal("6")));
        assertEquals(0, item.availableToPromiseTotal().compareTo(new BigDecimal("6")));
        assertThrows(InsufficientStockException.class,
            () -> item.confirmReservation(new BigDecimal("7")));
        assertThrows(IllegalArgumentException.class,
            () -> item.confirmReservation(null));
    }

    @Test
    void releaseRestoresAtp() {
        InventoryItem item = item();
        item.receive(TEN);
        item.reserve(new BigDecimal("4"));
        item.release(new BigDecimal("4"));
        assertEquals(0, item.quantityOnHandTotal().compareTo(TEN));
        assertEquals(0, item.availableToPromiseTotal().compareTo(TEN));
        assertThrows(IllegalArgumentException.class,
            () -> item.release(new BigDecimal("-1")));
    }
}
