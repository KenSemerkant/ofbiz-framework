package party.adapters.rest.persistence;

import java.util.LinkedHashMap;
import party.application.PartySerializer;
import party.domain.Party;
import party.domain.PartyKind;
import tools.jackson.databind.ObjectMapper;

/** Event payload shape consumed by identity-svc and the CDC consumers. */
public class JacksonPartySerializer implements PartySerializer {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String serialize(Party party) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("partyId", party.partyId());
        fields.put("kind", party.kind().name());
        if (party.kind() == PartyKind.PERSON) {
            fields.put("firstName", party.person().firstName());
            fields.put("lastName", party.person().lastName());
        } else {
            fields.put("groupName", party.group().groupName());
        }
        fields.put("roles", party.roles());
        return mapper.writeValueAsString(fields);
    }
}
