package inventory.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import inventory.domain.InsufficientStockException;
import inventory.domain.InventoryItem;
import inventory.domain.Reservation;

/**
 * P3-F2-S2 reservation saga: reserve→confirm/cancel. Reserve checks and
 * decrements ATP (QOH unchanged — goods still present until shipped);
 * confirm converts the reservation and decrements QOH; cancel is the
 * compensation and releases the ATP back. Oversell prevention relies on the
 * optimistic-lock version on the inventory item row (adapter layer).
 */
public class ReservationService {

    public static final String STOCK_RESERVED = "inventory.stock.reserved.v1";
    public static final String STOCK_CONFIRMED = "inventory.stock.confirmed.v1";
    public static final String STOCK_RELEASED = "inventory.stock.released.v1";

    private final InventoryItemStore items;
    private final ReservationStore reservations;
    private final OutboxWriter outbox;
    private final InventorySerializer serializer;

    public ReservationService(InventoryItemStore items, ReservationStore reservations,
            OutboxWriter outbox, InventorySerializer serializer) {
        this.items = items;
        this.reservations = reservations;
        this.outbox = outbox;
        this.serializer = serializer;
    }

    public Reservation reserve(String orderId, String orderItemSeqId, String productId,
            String facilityId, BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        List<InventoryItem> candidates = items.findByProductIdAndFacilityId(productId, facilityId);
        if (candidates.isEmpty()) {
            throw new NoSuchElementException("no inventory item for product " + productId
                + " at facility " + facilityId);
        }
        // serialized stock items hold one unit per item; bulk items are one row
        // with many units — first candidate with enough ATP wins
        InventoryItem item = candidates.stream().filter(c -> c.canReserve(quantity)).findFirst()
            .orElseThrow(() -> new InsufficientStockException(
                "insufficient available to promise for product " + productId + " at facility "
                    + facilityId));
        item.reserve(quantity);
        items.save(item);
        Reservation reservation = new Reservation(newReservationId(), orderId, orderItemSeqId,
            productId, facilityId, item.inventoryItemId(), quantity);
        reservations.save(reservation);
        outbox.append(STOCK_RESERVED, reservation.reservationId(),
            serializer.serializeReservation(reservation, item));
        return reservation;
    }

    public Reservation confirm(String reservationId) {
        Reservation reservation = load(reservationId);
        reservation.confirm();
        InventoryItem item = item(reservation.inventoryItemId());
        item.confirmReservation(reservation.quantity());
        items.save(item);
        reservations.save(reservation);
        outbox.append(STOCK_CONFIRMED, reservationId,
            serializer.serializeReservation(reservation, item));
        return reservation;
    }

    public Reservation cancel(String reservationId) {
        Reservation reservation = load(reservationId);
        reservation.cancel();
        InventoryItem item = item(reservation.inventoryItemId());
        item.release(reservation.quantity());
        items.save(item);
        reservations.save(reservation);
        outbox.append(STOCK_RELEASED, reservationId,
            serializer.serializeReservation(reservation, item));
        return reservation;
    }

    public java.util.Optional<Reservation> get(String reservationId) {
        return reservations.find(reservationId);
    }

    private Reservation load(String reservationId) {
        return reservations.find(reservationId).orElseThrow(
            () -> new NoSuchElementException("no reservation " + reservationId));
    }

    private InventoryItem item(String inventoryItemId) {
        return items.find(inventoryItemId).orElseThrow(
            () -> new NoSuchElementException("no inventory item " + inventoryItemId));
    }

    private static String newReservationId() {
        return "RES-" + UUID.randomUUID();
    }
}
