package inventory.domain;

/**
 * Business conflict: the stock op would overdraw available to promise or
 * drive a total negative (maps to 409 at the edge). Distinct from infra
 * failures, which must surface as 500.
 */
public class InsufficientStockException extends IllegalStateException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
