package humanres.domain;

import java.time.LocalDate;

/**
 * P6-T3 employment (Employment live slice): a party occupying a position
 * from a date until an optional thru date. Party linkage is by id only —
 * no party data duplication (FUTURESTATE §5.10).
 */
public final class Employment {

    private final String employmentId;
    private final String partyId;
    private final String emplPositionId;
    private final LocalDate fromDate;
    private final LocalDate thruDate;

    public Employment(String employmentId, String partyId, String emplPositionId,
            LocalDate fromDate, LocalDate thruDate) {
        if (employmentId == null || employmentId.isBlank()) {
            throw new IllegalArgumentException("employmentId must not be blank");
        }
        if (partyId == null || partyId.isBlank()) {
            throw new IllegalArgumentException("partyId must not be blank");
        }
        if (emplPositionId == null || emplPositionId.isBlank()) {
            throw new IllegalArgumentException("emplPositionId must not be blank");
        }
        if (fromDate == null) {
            throw new IllegalArgumentException("fromDate must not be null");
        }
        if (thruDate != null && thruDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("thruDate must not be before fromDate");
        }
        this.employmentId = employmentId;
        this.partyId = partyId;
        this.emplPositionId = emplPositionId;
        this.fromDate = fromDate;
        this.thruDate = thruDate;
    }

    public String employmentId() {
        return employmentId;
    }

    public String partyId() {
        return partyId;
    }

    public String emplPositionId() {
        return emplPositionId;
    }

    public LocalDate fromDate() {
        return fromDate;
    }

    public LocalDate thruDate() {
        return thruDate;
    }
}
