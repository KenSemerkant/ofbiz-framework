package party.adapters.rest.persistence;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import party.application.PartyStore;
import party.domain.Party;
import party.domain.PartyGroup;
import party.domain.PartyKind;
import party.domain.PartyRelationship;
import party.domain.Person;

/** JPA adapter for the PartyStore port. */
@Repository
public class JpaPartyStore implements PartyStore {

    private final PartyRepository parties;

    public JpaPartyStore(PartyRepository parties) {
        this.parties = parties;
    }

    @Override
    public Optional<Party> find(String partyId) {
        return parties.findById(partyId).map(this::toDomain);
    }

    @Override
    public void save(Party party) {
        parties.save(new PartyEntity(party.partyId(), party.kind().name(),
            party.person() == null ? null : party.person().firstName(),
            party.person() == null ? null : party.person().lastName(),
            party.group() == null ? null : party.group().groupName(),
            party.roles()));
    }

    private Party toDomain(PartyEntity entity) {
        PartyKind kind = PartyKind.valueOf(entity.getKind());
        Person person = kind == PartyKind.PERSON
            ? new Person(entity.getFirstName(), entity.getLastName())
            : null;
        PartyGroup group = kind == PartyKind.PARTY_GROUP
            ? new PartyGroup(entity.getGroupName())
            : null;
        return new Party(entity.getPartyId(), kind, person, group, entity.getRoles(), null);
    }
}
