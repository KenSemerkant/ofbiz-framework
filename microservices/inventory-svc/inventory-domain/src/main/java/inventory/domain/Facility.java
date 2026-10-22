package inventory.domain;

/**
 * A warehouse or store facility (OFBiz Facility slice, P3-F2-S1): identity,
 * display name and type. Owner party, contact mechs and locations wait for a
 * story that needs them.
 */
public record Facility(String facilityId, String facilityName, String facilityTypeId) {

    public Facility {
        if (facilityId == null || facilityId.isBlank()) {
            throw new IllegalArgumentException("facilityId must not be blank");
        }
        if (facilityName == null || facilityName.isBlank()) {
            throw new IllegalArgumentException("facilityName must not be blank");
        }
        if (facilityTypeId != null && facilityTypeId.isBlank()) {
            throw new IllegalArgumentException("facilityTypeId must not be blank");
        }
    }
}
