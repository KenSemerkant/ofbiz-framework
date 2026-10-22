package inventory.domain;

import java.math.BigDecimal;

/**
 * Stock held of one product at one facility (OFBiz InventoryItem slice,
 * P3-F2-S1). OFBiz keeps two running totals — quantity on hand (goods
 * physically present) and available to promise (on hand minus reserved) — and
 * every stock op mutates the pair consistently.
 *
 * IllegalArgumentException = malformed input (bad request);
 * IllegalStateException = business conflict (insufficient stock) — the adapter
 * layer maps the two to distinct HTTP statuses.
 */
public final class InventoryItem {

    private final String inventoryItemId;
    private final String productId;
    private final String facilityId;
    private final String serialNumber;
    private final BigDecimal unitCost;
    private BigDecimal quantityOnHandTotal = BigDecimal.ZERO;
    private BigDecimal availableToPromiseTotal = BigDecimal.ZERO;

    public InventoryItem(String inventoryItemId, String productId, String facilityId,
            String serialNumber, BigDecimal unitCost) {
        this(inventoryItemId, productId, facilityId, serialNumber, unitCost,
            BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** Rehydration constructor: totals come from persistence and are trusted >= 0 there. */
    public InventoryItem(String inventoryItemId, String productId, String facilityId,
            String serialNumber, BigDecimal unitCost, BigDecimal quantityOnHandTotal,
            BigDecimal availableToPromiseTotal) {
        if (inventoryItemId == null || inventoryItemId.isBlank()) {
            throw new IllegalArgumentException("inventoryItemId must not be blank");
        }
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }
        if (facilityId == null || facilityId.isBlank()) {
            throw new IllegalArgumentException("facilityId must not be blank");
        }
        if (serialNumber != null && serialNumber.isBlank()) {
            throw new IllegalArgumentException("serialNumber must not be blank");
        }
        if (quantityOnHandTotal == null || quantityOnHandTotal.signum() < 0) {
            throw new IllegalArgumentException("quantityOnHandTotal must not be negative");
        }
        if (availableToPromiseTotal == null || availableToPromiseTotal.signum() < 0) {
            throw new IllegalArgumentException("availableToPromiseTotal must not be negative");
        }
        this.inventoryItemId = inventoryItemId;
        this.productId = productId;
        this.facilityId = facilityId;
        this.serialNumber = serialNumber;
        this.unitCost = unitCost;
        this.quantityOnHandTotal = quantityOnHandTotal;
        this.availableToPromiseTotal = availableToPromiseTotal;
    }

    /** Goods arrive (P3-F2-S1 receive): both totals rise. */
    public void receive(BigDecimal quantity) {
        requirePositive(quantity);
        quantityOnHandTotal = quantityOnHandTotal.add(quantity);
        availableToPromiseTotal = availableToPromiseTotal.add(quantity);
    }

    /** Variance correction (P3-F2-S1 adjust): both totals move the same direction. */
    public void adjust(BigDecimal quantity) {
        if (quantity == null || quantity.signum() == 0) {
            throw new IllegalArgumentException("adjustment quantity must not be zero");
        }
        BigDecimal qoh = quantityOnHandTotal.add(quantity);
        BigDecimal atp = availableToPromiseTotal.add(quantity);
        if (qoh.signum() < 0 || atp.signum() < 0) {
            throw new InsufficientStockException(
                "adjustment would drive stock negative (qoh=" + qoh + ", atp=" + atp + ")");
        }
        quantityOnHandTotal = qoh;
        availableToPromiseTotal = atp;
    }

    /** Move stock onto another item (P3-F2-S1 transfer): source ATP must cover it. */
    public void transferTo(InventoryItem target, BigDecimal quantity) {
        requirePositive(quantity);
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (target.inventoryItemId.equals(inventoryItemId)) {
            throw new IllegalArgumentException("cannot transfer an item onto itself");
        }
        if (availableToPromiseTotal.compareTo(quantity) < 0) {
            throw new InsufficientStockException("insufficient available to promise ("
                + availableToPromiseTotal + " < " + quantity + ")");
        }
        quantityOnHandTotal = quantityOnHandTotal.subtract(quantity);
        availableToPromiseTotal = availableToPromiseTotal.subtract(quantity);
        target.quantityOnHandTotal = target.quantityOnHandTotal.add(quantity);
        target.availableToPromiseTotal = target.availableToPromiseTotal.add(quantity);
    }

    private static void requirePositive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    public String inventoryItemId() {
        return inventoryItemId;
    }

    public String productId() {
        return productId;
    }

    public String facilityId() {
        return facilityId;
    }

    public String serialNumber() {
        return serialNumber;
    }

    public BigDecimal unitCost() {
        return unitCost;
    }

    public BigDecimal quantityOnHandTotal() {
        return quantityOnHandTotal;
    }

    public BigDecimal availableToPromiseTotal() {
        return availableToPromiseTotal;
    }
}
