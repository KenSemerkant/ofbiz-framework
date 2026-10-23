package finance.domain;

import java.time.LocalDate;

/**
 * P5-F2-S1 fiscal period (CustomTimePeriod, ledger slice — cross-file
 * exception per finance-svc MAPPING.md). Closing is one-way; postings in
 * a closed period are refused by the transaction guard.
 */
public final class Period {

    private final String periodId;
    private final LocalDate fromDate;
    private final LocalDate thruDate;
    private boolean closed;

    public Period(String periodId, LocalDate fromDate, LocalDate thruDate) {
        if (periodId == null || periodId.isBlank()) {
            throw new IllegalArgumentException("periodId must not be blank");
        }
        if (fromDate == null || thruDate == null || thruDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("thruDate must not be before fromDate");
        }
        this.periodId = periodId;
        this.fromDate = fromDate;
        this.thruDate = thruDate;
    }

    /** One-way close: a closed period never reopens (reversing entries instead). */
    public void close() {
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }

    public static Period rehydrate(String periodId, LocalDate fromDate, LocalDate thruDate,
            boolean closed) {
        Period period = new Period(periodId, fromDate, thruDate);
        period.closed = closed;
        return period;
    }

    public String periodId() {
        return periodId;
    }

    public LocalDate fromDate() {
        return fromDate;
    }

    public LocalDate thruDate() {
        return thruDate;
    }
}
