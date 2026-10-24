package humanres.application;

/** Position ids are caller-supplied; a recreate is a typed 409. */
public final class DuplicatePositionException extends RuntimeException {

    public DuplicatePositionException(String emplPositionId) {
        super("empl position %s already exists".formatted(emplPositionId));
    }
}
