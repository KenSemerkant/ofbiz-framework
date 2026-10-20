package identity.adapters.rest.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "party_projection")
public class PartyProjectionEntity {

    @Id
    private String partyId;

    private String payload;

    private Instant updatedAt = Instant.now();

    protected PartyProjectionEntity() {}

    public PartyProjectionEntity(String partyId, String payload, Instant updatedAt) {
        this.partyId = partyId;
        this.payload = payload;
        this.updatedAt = updatedAt;
    }

    public String getPartyId() {
        return partyId;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
