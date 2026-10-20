package identity.domain;

import java.time.Instant;

/**
 * Read-model copy of a monolith party (P1-F1-S3 stub for Phase 2 identity
 * integration: JWT claims will be enriched from this projection).
 */
public record PartyProjection(String partyId, String payload, Instant updatedAt) {
    public PartyProjection {
        if (partyId == null || partyId.isBlank()) {
            throw new IllegalArgumentException("partyId must not be blank");
        }
    }
}
