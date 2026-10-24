package workeffort.adapters.rest.persistence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.ObjectMapper;
import workeffort.domain.TimesheetEntry;

/** JSON mapping for the inline timesheet entries. */
public final class TimesheetEntryJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    record EntryDto(String workEffortId, BigDecimal hours, String rateTypeId) {
    }

    public static String serialize(List<TimesheetEntry> entries) {
        return MAPPER.writeValueAsString(entries.stream()
            .map(e -> new EntryDto(e.workEffortId(), e.hours(), e.rateTypeId()))
            .toList());
    }

    public static List<TimesheetEntry> deserialize(String json) {
        List<EntryDto> dtos = MAPPER.readValue(json,
            MAPPER.getTypeFactory().constructCollectionType(ArrayList.class, EntryDto.class));
        return dtos.stream()
            .map(dto -> new TimesheetEntry(dto.workEffortId(), dto.hours(), dto.rateTypeId()))
            .toList();
    }

    private TimesheetEntryJson() {
    }
}
