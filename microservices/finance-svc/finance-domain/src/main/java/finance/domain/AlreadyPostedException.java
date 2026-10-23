package finance.domain;

/** Journal entries are immutable once posted (P5-F2-S1). */
public final class AlreadyPostedException extends RuntimeException {

    public AlreadyPostedException(String acctgTransId) {
        super("transaction %s is already posted".formatted(acctgTransId));
    }
}
