package party.domain;

import java.time.Instant;

/**
 * Directed relationship from this party to another (OFBiz PartyRelationship).
 * thruDate is exclusive-open in OFBiz; here it must simply postdate fromDate.
 */
public record PartyRelationship(String toPartyId, String relationshipTypeId,
        Instant fromDate, Instant thruDate) {

    public PartyRelationship {
        if (toPartyId == null || toPartyId.isBlank()) {
            throw new IllegalArgumentException("toPartyId must not be blank");
        }
        if (relationshipTypeId == null || relationshipTypeId.isBlank()) {
            throw new IllegalArgumentException("relationshipTypeId must not be blank");
        }
        if (fromDate == null) {
            throw new IllegalArgumentException("fromDate must not be null");
        }
        if (thruDate != null && !thruDate.isAfter(fromDate)) {
            throw new IllegalArgumentException("thruDate must be after fromDate");
        }
    }

    public boolean activeAt(Instant at) {
        return !at.isBefore(fromDate) && (thruDate == null || at.isBefore(thruDate));
    }
}
