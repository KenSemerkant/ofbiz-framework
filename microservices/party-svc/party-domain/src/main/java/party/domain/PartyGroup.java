package party.domain;

/** Organization name (OFBiz PartyGroup slice). */
public record PartyGroup(String groupName) {

    public PartyGroup {
        if (groupName == null || groupName.isBlank()) {
            throw new IllegalArgumentException("groupName must not be blank");
        }
    }
}
