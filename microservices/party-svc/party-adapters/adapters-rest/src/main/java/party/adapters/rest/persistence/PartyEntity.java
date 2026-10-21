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

@Entity
@Table(name = "parties")
public class PartyEntity {

    @Id
    @Column(name = "party_id")
    private String partyId;

    @Column(nullable = false)
    private String kind;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "group_name")
    private String groupName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "party_roles", joinColumns = @JoinColumn(name = "party_id"))
    @Column(name = "role_type_id", nullable = false)
    private Set<String> roles = new LinkedHashSet<>();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public PartyEntity() {
    }

    public PartyEntity(String partyId, String kind, String firstName, String lastName,
            String groupName, Set<String> roles) {
        this.partyId = partyId;
        this.kind = kind;
        this.firstName = firstName;
        this.lastName = lastName;
        this.groupName = groupName;
        this.roles = new LinkedHashSet<>(roles);
    }

    public String getPartyId() {
        return partyId;
    }

    public String getKind() {
        return kind;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getGroupName() {
        return groupName;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
