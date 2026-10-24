package workeffort.domain;

import java.math.BigDecimal;

/**
 * P6-T2 timesheet line (TimeEntry live slice, inline-JSON precedent):
 * hours booked against one work effort at one rate. The effort itself
 * lives in the WorkEffort aggregate — referenced by id only.
 */
public record TimesheetEntry(String workEffortId, BigDecimal hours, String rateTypeId) {

    public TimesheetEntry {
        if (workEffortId == null || workEffortId.isBlank()) {
            throw new IllegalArgumentException("workEffortId must not be blank");
        }
        if (hours == null || hours.signum() <= 0) {
            throw new IllegalArgumentException("hours must be positive");
        }
        if (rateTypeId == null || rateTypeId.isBlank()) {
            throw new IllegalArgumentException("rateTypeId must not be blank");
        }
    }
}
