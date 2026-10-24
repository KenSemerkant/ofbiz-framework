package humanres.domain;

/**
 * P6-T3 position (EmplPosition live slice): a slot in the org chart, typed
 * and optionally reporting to another position. Free-form status ids keep
 * the vocabulary lookup-free (humanres-svc MAPPING.md).
 */
public final class EmplPosition {

    private final String emplPositionId;
    private final String emplPositionTypeId;
    private final String reportingToEmplPositionId;
    private final String statusId;

    public EmplPosition(String emplPositionId, String emplPositionTypeId,
            String reportingToEmplPositionId, String statusId) {
        if (emplPositionId == null || emplPositionId.isBlank()) {
            throw new IllegalArgumentException("emplPositionId must not be blank");
        }
        if (emplPositionTypeId == null || emplPositionTypeId.isBlank()) {
            throw new IllegalArgumentException("emplPositionTypeId must not be blank");
        }
        if (statusId == null || statusId.isBlank()) {
            throw new IllegalArgumentException("statusId must not be blank");
        }
        this.emplPositionId = emplPositionId;
        this.emplPositionTypeId = emplPositionTypeId;
        this.reportingToEmplPositionId = reportingToEmplPositionId;
        this.statusId = statusId;
    }

    public String emplPositionId() {
        return emplPositionId;
    }

    public String emplPositionTypeId() {
        return emplPositionTypeId;
    }

    public String reportingToEmplPositionId() {
        return reportingToEmplPositionId;
    }

    public String statusId() {
        return statusId;
    }
}
