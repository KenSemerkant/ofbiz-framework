package humanres.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** party_projection row: party id + names JSON (display data only). */
@Entity
@Table(name = "party_projection")
public class PartyProjectionEntity {

    @Id
    @Column(name = "party_id")
    private String partyId;

    @Column(name = "names", nullable = false)
    private String names;

    @Column(name = "projected_at", nullable = false)
    private Instant projectedAt = Instant.now();

    public PartyProjectionEntity() {
    }

    public PartyProjectionEntity(String partyId, String names) {
        this.partyId = partyId;
        this.names = names;
    }

    public String getPartyId() {
        return partyId;
    }

    public String getNames() {
        return names;
    }

    public Instant getProjectedAt() {
        return projectedAt;
    }
}
