package billing.adapters.rest.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.ObjectMapper;
import billing.domain.InvoiceLine;
import billing.domain.InvoiceStatus;
import billing.domain.InvoiceStatusChange;

/** JSON mapping for the inline invoice lines + status history. */
final class InvoiceJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    record LineDto(String invoiceItemSeqId, String invoiceItemTypeId, String description,
            BigDecimal quantity, BigDecimal amount) {
    }

    record StatusDto(String status, Instant changedAt, String note) {
    }

    public static String serializeLines(List<InvoiceLine> lines) {
        return MAPPER.writeValueAsString(lines.stream()
            .map(l -> new LineDto(l.invoiceItemSeqId(), l.invoiceItemTypeId(), l.description(),
                l.quantity(), l.amount()))
            .toList());
    }

    public static List<InvoiceLine> deserializeLines(String json) {
        List<LineDto> dtos = MAPPER.readValue(json,
            MAPPER.getTypeFactory().constructCollectionType(ArrayList.class, LineDto.class));
        return dtos.stream()
            .map(dto -> new InvoiceLine(dto.invoiceItemSeqId(), dto.invoiceItemTypeId(),
                dto.description(), dto.quantity(), dto.amount()))
            .toList();
    }

    public static String serializeHistory(List<InvoiceStatusChange> history) {
        return MAPPER.writeValueAsString(history.stream()
            .map(change -> new StatusDto(change.status().name(), change.changedAt(),
                change.note()))
            .toList());
    }

    public static List<InvoiceStatusChange> deserializeHistory(String json) {
        List<StatusDto> dtos = MAPPER.readValue(json,
            MAPPER.getTypeFactory().constructCollectionType(ArrayList.class, StatusDto.class));
        return dtos.stream()
            .map(dto -> new InvoiceStatusChange(InvoiceStatus.valueOf(dto.status()),
                dto.changedAt(), dto.note()))
            .toList();
    }

    private InvoiceJson() {
    }
}
