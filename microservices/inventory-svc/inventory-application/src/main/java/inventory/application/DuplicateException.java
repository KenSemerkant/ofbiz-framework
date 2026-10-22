package inventory.application;

/**
 * A create targeted an id that already exists (maps to 409 at the edge).
 * Deliberately not a bare IllegalStateException: infra failures (e.g. outbox
 * down) also surface as IllegalStateException and must map to 500 instead.
 */
public class DuplicateException extends IllegalStateException {

    public DuplicateException(String message) {
        super(message);
    }
}
