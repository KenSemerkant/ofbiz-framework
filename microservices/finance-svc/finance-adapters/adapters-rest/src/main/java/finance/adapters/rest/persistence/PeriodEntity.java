package finance.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** periods row (P5-F2-S1, CustomTimePeriod ledger slice). */
@Entity
@Table(name = "periods")
public class PeriodEntity {

    @Id
    @Column(name = "period_id")
    private String periodId;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "thru_date", nullable = false)
    private LocalDate thruDate;

    @Column(nullable = false)
    private boolean closed;

    public PeriodEntity() {
    }

    public PeriodEntity(String periodId, LocalDate fromDate, LocalDate thruDate,
            boolean closed) {
        this.periodId = periodId;
        this.fromDate = fromDate;
        this.thruDate = thruDate;
        this.closed = closed;
    }

    public String getPeriodId() {
        return periodId;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getThruDate() {
        return thruDate;
    }

    public boolean isClosed() {
        return closed;
    }
}
