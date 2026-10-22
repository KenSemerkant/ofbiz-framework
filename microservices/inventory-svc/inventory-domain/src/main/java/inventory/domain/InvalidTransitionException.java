package inventory.domain;

/**
 * Business conflict: a lifecycle op was attempted from a state that does not
 * allow it (maps to 409 at the edge). Distinct from infra failures, which
 * must surface as 500.
 */
public class InvalidTransitionException extends IllegalStateException {

    public InvalidTransitionException(String message) {
        super(message);
    }
}
