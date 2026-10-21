package party.adapters.rest.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import party.domain.ContactMech;
import party.domain.ContactMechPurpose;
import party.domain.ContactMechType;
import party.domain.PartyContactMech;
import tools.jackson.databind.ObjectMapper;

/**
 * JSON mapping for the inline contact_mechs column and the event payload
 * (same shape both places, P2-F1-S2).
 */
public final class ContactMechJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record MechDto(String contactMechId, String type, String value, Set<String> purposes) {
    }

    private ContactMechJson() {
    }

    public static String serialize(List<PartyContactMech> mechs) {
        var dtos = mechs.stream()
            .map(m -> new MechDto(m.mech().contactMechId(), m.mech().type().name(),
                m.mech().value(),
                m.purposes().stream().map(Enum::name).collect(Collectors.toSet())))
            .toList();
        return MAPPER.writeValueAsString(dtos);
    }

    public static List<PartyContactMech> deserialize(String json) {
        List<MechDto> dtos = MAPPER.readValue(json,
            MAPPER.getTypeFactory().constructCollectionType(ArrayList.class, MechDto.class));
        return dtos.stream()
            .map(dto -> new PartyContactMech(
                new ContactMech(dto.contactMechId(), ContactMechType.valueOf(dto.type()),
                    dto.value()),
                dto.purposes().stream().map(ContactMechPurpose::valueOf)
                    .collect(Collectors.toSet())))
            .toList();
    }
}
