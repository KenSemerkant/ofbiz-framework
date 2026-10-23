package order.application;

/** Saga rejection (P4-F1-S2): no applicable price for a requested line. */
public class PriceUnavailableException extends RuntimeException {

    public PriceUnavailableException(String productId) {
        super("no applicable price for product " + productId);
    }
}
