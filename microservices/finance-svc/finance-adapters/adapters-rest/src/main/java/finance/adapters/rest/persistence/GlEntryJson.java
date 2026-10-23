package finance.adapters.rest.persistence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import finance.domain.DebitCredit;
import finance.domain.GlEntry;
import tools.jackson.databind.ObjectMapper;

/** JSON mapping for the inline journal entries. */
final class GlEntryJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    record EntryDto(String glAccountId, BigDecimal amount, String debitCredit) {
    }

    public static String serialize(List<GlEntry> entries) {
        return MAPPER.writeValueAsString(entries.stream()
            .map(e -> new EntryDto(e.glAccountId(), e.amount(), e.debitCredit().name()))
            .toList());
    }

    public static List<GlEntry> deserialize(String json) {
        List<EntryDto> dtos = MAPPER.readValue(json,
            MAPPER.getTypeFactory().constructCollectionType(ArrayList.class, EntryDto.class));
        return dtos.stream()
            .map(dto -> new GlEntry(dto.glAccountId(), dto.amount(),
                DebitCredit.valueOf(dto.debitCredit())))
            .toList();
    }

    private GlEntryJson() {
    }
}
