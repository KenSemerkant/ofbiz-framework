package party.adapters.rest.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/** Monolith CDC projection row (P2-F2-S2). */
@Entity
@Table(name = "monolith_parties")
public class MonolithPartyEntity {

    @Id
    @Column(name = "party_id")
    private String partyId;

    @Column(name = "party_type_id")
    private String partyTypeId;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "group_name")
    private String groupName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "monolith_party_roles",
        joinColumns = @JoinColumn(name = "party_id"))
    @Column(name = "role_type_id", nullable = false)
    private Set<String> roles = new LinkedHashSet<>();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public MonolithPartyEntity() {
    }

    public MonolithPartyEntity(String partyId) {
        this.partyId = partyId;
    }

    public String getPartyId() {
        return partyId;
    }

    public String getPartyTypeId() {
        return partyTypeId;
    }

    public void setPartyTypeId(String partyTypeId) {
        this.partyTypeId = partyTypeId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public Set<String> getRoles() {
        return roles;
    }
}
