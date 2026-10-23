package order.application;

/** Saga failure (P4-F2-S1): stock cannot cover a line — compensates. */
public class InventoryUnavailableException extends RuntimeException {

    public InventoryUnavailableException(String message) {
        super(message);
    }
}
