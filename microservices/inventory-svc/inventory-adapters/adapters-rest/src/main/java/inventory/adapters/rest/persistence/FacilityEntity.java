package inventory.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "facilities")
public class FacilityEntity {

    @Id
    @Column(name = "facility_id")
    private String facilityId;

    @Column(name = "facility_name", nullable = false)
    private String facilityName;

    @Column(name = "facility_type_id")
    private String facilityTypeId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public FacilityEntity() {
    }

    public FacilityEntity(String facilityId, String facilityName, String facilityTypeId) {
        this.facilityId = facilityId;
        this.facilityName = facilityName;
        this.facilityTypeId = facilityTypeId;
    }

    public String getFacilityId() {
        return facilityId;
    }

    public String getFacilityName() {
        return facilityName;
    }

    public String getFacilityTypeId() {
        return facilityTypeId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
