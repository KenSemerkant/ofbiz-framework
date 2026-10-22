package inventory.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * P3-F2-S3 contract: the status machine is forward-only — an illegal
 * transition must fail loudly (it maps to 409 at the edge), and the shipment
 * becomes immutable once it leaves CREATED.
 */
class ShipmentTest {

    private static final BigDecimal QTY = new BigDecimal("2");

    private Shipment shipment() {
        return new Shipment("SH-1", "ORD-1");
    }

    @Test
    void itemsAreNumberedInInsertionOrder() {
        Shipment shipment = shipment();
        shipment.addShipmentItem("WIDGET", QTY, "II1");
        shipment.addShipmentItem("GIZMO", BigDecimal.ONE, "II2");
        assertEquals(List.of("00001", "00002"),
            shipment.items().stream().map(ShipmentItem::shipmentItemSeqId).toList());
        assertEquals("WIDGET", shipment.items().get(0).productId());
        assertEquals(0, shipment.items().get(1).quantity().compareTo(BigDecimal.ONE));
        assertEquals("II2", shipment.items().get(1).inventoryItemId());
    }

    @Test
    void forwardTransitionsAndGuards() {
        Shipment shipment = shipment();
        assertEquals(Shipment.Status.CREATED, shipment.status());
        // cannot skip or repeat transitions
        assertThrows(InvalidTransitionException.class, shipment::ship);
        shipment.pack();
        assertEquals(Shipment.Status.PACKED, shipment.status());
        assertThrows(InvalidTransitionException.class, shipment::pack);
        shipment.ship();
        assertEquals(Shipment.Status.SHIPPED, shipment.status());
        assertThrows(InvalidTransitionException.class, shipment::pack);
        assertThrows(InvalidTransitionException.class, shipment::ship);
    }

    @Test
    void itemsFreezeOncePacking() {
        Shipment shipment = shipment();
        shipment.addShipmentItem("WIDGET", QTY, "II1");
        shipment.pack();
        assertThrows(InvalidTransitionException.class,
            () -> shipment.addShipmentItem("WIDGET", QTY, "II1"));
    }

    @Test
    void guards() {
        assertThrows(IllegalArgumentException.class, () -> new Shipment(" ", null));
        assertThrows(IllegalArgumentException.class, () -> new Shipment("SH", " "));
        assertThrows(IllegalArgumentException.class,
            () -> new Shipment("SH", null).addShipmentItem(" ", QTY, "II1"));
        assertThrows(IllegalArgumentException.class,
            () -> new Shipment("SH", null).addShipmentItem("P", BigDecimal.ZERO, "II1"));
        assertThrows(IllegalArgumentException.class,
            () -> new Shipment("SH", null).addShipmentItem("P", QTY, null));
    }

    @Test
    void rehydrateRestoresStatusAndItems() {
        Shipment shipment = Shipment.rehydrate("SH-9", "ORD-9", Shipment.Status.PACKED,
            List.of(new ShipmentItem("00001", "WIDGET", QTY, "II1")));
        assertEquals(Shipment.Status.PACKED, shipment.status());
        assertEquals("ORD-9", shipment.orderId());
        assertEquals(1, shipment.items().size());
        assertThrows(InvalidTransitionException.class, shipment::pack);
        shipment.ship();
        assertEquals(Shipment.Status.SHIPPED, shipment.status());
    }

    @Test
    void orderlessShipmentIsAllowed() {
        Shipment shipment = new Shipment("SH-2", null);
        assertEquals(null, shipment.orderId());
        assertEquals(0, shipment.items().size());
    }
}
