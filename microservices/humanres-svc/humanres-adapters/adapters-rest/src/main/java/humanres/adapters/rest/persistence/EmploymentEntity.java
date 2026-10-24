package humanres.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** employments row (P6-T3, Employment live slice). */
@Entity
@Table(name = "employments")
public class EmploymentEntity {

    @Id
    @Column(name = "employment_id")
    private String employmentId;

    @Column(name = "party_id", nullable = false)
    private String partyId;

    @Column(name = "empl_position_id", nullable = false)
    private String emplPositionId;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "thru_date")
    private LocalDate thruDate;

    public EmploymentEntity() {
    }

    public EmploymentEntity(String employmentId, String partyId, String emplPositionId,
            LocalDate fromDate, LocalDate thruDate) {
        this.employmentId = employmentId;
        this.partyId = partyId;
        this.emplPositionId = emplPositionId;
        this.fromDate = fromDate;
        this.thruDate = thruDate;
    }

    public String getEmploymentId() {
        return employmentId;
    }

    public String getPartyId() {
        return partyId;
    }

    public String getEmplPositionId() {
        return emplPositionId;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getThruDate() {
        return thruDate;
    }
}
