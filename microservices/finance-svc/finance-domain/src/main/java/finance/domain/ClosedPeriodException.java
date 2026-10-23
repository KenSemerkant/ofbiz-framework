package finance.domain;

/** P5-F2-S1: a closed fiscal period blocks all postings inside it. */
public final class ClosedPeriodException extends RuntimeException {

    public ClosedPeriodException(String customTimePeriodId) {
        super("period %s is closed".formatted(customTimePeriodId));
    }
}
