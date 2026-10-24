package humanres.application;

/** Employment ids are caller-supplied; a recreate is a typed 409. */
public final class DuplicateEmploymentException extends RuntimeException {

    public DuplicateEmploymentException(String employmentId) {
        super("employment %s already exists".formatted(employmentId));
    }
}
