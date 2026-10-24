package humanres.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** empl_positions row (P6-T3, EmplPosition live slice). */
@Entity
@Table(name = "empl_positions")
public class EmplPositionEntity {

    @Id
    @Column(name = "empl_position_id")
    private String emplPositionId;

    @Column(name = "empl_position_type_id", nullable = false)
    private String emplPositionTypeId;

    @Column(name = "reporting_to_empl_position_id")
    private String reportingToEmplPositionId;

    @Column(name = "status_id", nullable = false)
    private String statusId;

    public EmplPositionEntity() {
    }

    public EmplPositionEntity(String emplPositionId, String emplPositionTypeId,
            String reportingToEmplPositionId, String statusId) {
        this.emplPositionId = emplPositionId;
        this.emplPositionTypeId = emplPositionTypeId;
        this.reportingToEmplPositionId = reportingToEmplPositionId;
        this.statusId = statusId;
    }

    public String getEmplPositionId() {
        return emplPositionId;
    }

    public String getEmplPositionTypeId() {
        return emplPositionTypeId;
    }

    public String getReportingToEmplPositionId() {
        return reportingToEmplPositionId;
    }

    public String getStatusId() {
        return statusId;
    }
}
