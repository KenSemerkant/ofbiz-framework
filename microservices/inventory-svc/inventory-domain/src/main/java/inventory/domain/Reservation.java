package inventory.domain;

import java.math.BigDecimal;

/**
 * A stock reservation against one inventory item (P3-F2-S2): reserve draws
 * down ATP, confirm ships the goods (QOH down, ATP already down), cancel
 * releases the ATP back — the compensation. Replaces OFBiz
 * OrderItemShipGrpInvRes semaphore-style oversell prevention.
 */
public final class Reservation {

    public enum Status { CREATED, CONFIRMED, RELEASED }

    private final String reservationId;
    private final String orderId;
    private final String orderItemSeqId;
    private final String productId;
    private final String facilityId;
    private final String inventoryItemId;
    private final BigDecimal quantity;
    private Status status = Status.CREATED;

    public Reservation(String reservationId, String orderId, String orderItemSeqId,
            String productId, String facilityId, String inventoryItemId, BigDecimal quantity) {
        if (reservationId == null || reservationId.isBlank()) {
            throw new IllegalArgumentException("reservationId must not be blank");
        }
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        if (orderItemSeqId == null || orderItemSeqId.isBlank()) {
            throw new IllegalArgumentException("orderItemSeqId must not be blank");
        }
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }
        if (facilityId == null || facilityId.isBlank()) {
            throw new IllegalArgumentException("facilityId must not be blank");
        }
        if (inventoryItemId == null || inventoryItemId.isBlank()) {
            throw new IllegalArgumentException("inventoryItemId must not be blank");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.reservationId = reservationId;
        this.orderId = orderId;
        this.orderItemSeqId = orderItemSeqId;
        this.productId = productId;
        this.facilityId = facilityId;
        this.inventoryItemId = inventoryItemId;
        this.quantity = quantity;
    }

    /** Goods leave the building: only a live reservation can confirm. */
    public void confirm() {
        requireCreated("confirm");
        status = Status.CONFIRMED;
    }

    /** Compensation: give the ATP back. Only a live reservation can cancel. */
    public void cancel() {
        requireCreated("cancel");
        status = Status.RELEASED;
    }

    /** Rehydration factory: the persisted status is trusted. */
    public static Reservation rehydrate(String reservationId, String orderId,
            String orderItemSeqId, String productId, String facilityId, String inventoryItemId,
            BigDecimal quantity, Status status) {
        Reservation reservation = new Reservation(reservationId, orderId, orderItemSeqId,
            productId, facilityId, inventoryItemId, quantity);
        reservation.status = status;
        return reservation;
    }

    private void requireCreated(String op) {
        if (status != Status.CREATED) {
            throw new InvalidTransitionException(
                "cannot " + op + " a reservation that is " + status);
        }
    }

    public String reservationId() {
        return reservationId;
    }

    public String orderId() {
        return orderId;
    }

    public String orderItemSeqId() {
        return orderItemSeqId;
    }

    public String productId() {
        return productId;
    }

    public String facilityId() {
        return facilityId;
    }

    public String inventoryItemId() {
        return inventoryItemId;
    }

    public BigDecimal quantity() {
        return quantity;
    }

    public Status status() {
        return status;
    }
}
