package order.adapters.rest.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import order.domain.OrderLine;
import order.domain.OrderStatus;
import order.domain.OrderStatusChange;
import order.domain.ReturnLine;
import tools.jackson.databind.ObjectMapper;

/** JSON mapping for inline line/history columns and event payloads. */
public final class OrderJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record LineDto(String orderItemSeqId, String productId, String facilityId,
            BigDecimal quantity, BigDecimal unitPrice, String currencyUomId) {
    }

    public record StatusDto(String status, Instant changedAt, String note) {
    }

    public record ReturnLineDto(String productId, String facilityId, BigDecimal quantity) {
    }

    private OrderJson() {
    }

    public static String serializeLines(List<OrderLine> lines) {
        return MAPPER.writeValueAsString(lines.stream()
            .map(line -> new LineDto(line.orderItemSeqId(), line.productId(), line.facilityId(),
                line.quantity(), line.unitPrice(), line.currencyUomId()))
            .toList());
    }

    public static List<OrderLine> deserializeLines(String json) {
        List<LineDto> dtos = MAPPER.readValue(json,
            MAPPER.getTypeFactory().constructCollectionType(ArrayList.class, LineDto.class));
        return dtos.stream()
            .map(dto -> new OrderLine(dto.orderItemSeqId(), dto.productId(), dto.facilityId(),
                dto.quantity(), dto.unitPrice(), dto.currencyUomId()))
            .toList();
    }

    public static String serializeHistory(List<OrderStatusChange> history) {
        return MAPPER.writeValueAsString(history.stream()
            .map(change -> new StatusDto(change.status().name(), change.changedAt(),
                change.note()))
            .toList());
    }

    public static List<OrderStatusChange> deserializeHistory(String json) {
        List<StatusDto> dtos = MAPPER.readValue(json,
            MAPPER.getTypeFactory().constructCollectionType(ArrayList.class, StatusDto.class));
        return dtos.stream()
            .map(dto -> new OrderStatusChange(OrderStatus.valueOf(dto.status()),
                dto.changedAt(), dto.note()))
            .toList();
    }

    public static String serializeReturnLines(List<ReturnLine> lines) {
        return MAPPER.writeValueAsString(lines.stream()
            .map(line -> new ReturnLineDto(line.productId(), line.facilityId(), line.quantity()))
            .toList());
    }

    public static List<ReturnLine> deserializeReturnLines(String json) {
        List<ReturnLineDto> dtos = MAPPER.readValue(json,
            MAPPER.getTypeFactory().constructCollectionType(ArrayList.class, ReturnLineDto.class));
        return dtos.stream()
            .map(dto -> new ReturnLine(dto.productId(), dto.facilityId(), dto.quantity()))
            .toList();
    }

    public record ReceiptDto(String reservationId, String inventoryItemId, String productId,
            String facilityId) {
    }

    public static String serializeReceipts(
            List<order.application.InventoryGateway.ReservationReceipt> receipts) {
        return MAPPER.writeValueAsString(receipts.stream()
            .map(r -> new ReceiptDto(r.reservationId(), r.inventoryItemId(), r.productId(),
                r.facilityId()))
            .toList());
    }

    public static List<order.application.InventoryGateway.ReservationReceipt> deserializeReceipts(
            String json) {
        List<ReceiptDto> dtos = MAPPER.readValue(json,
            MAPPER.getTypeFactory().constructCollectionType(ArrayList.class, ReceiptDto.class));
        return dtos.stream()
            .map(dto -> new order.application.InventoryGateway.ReservationReceipt(
                dto.reservationId(), dto.inventoryItemId(), dto.productId(), dto.facilityId()))
            .toList();
    }
}
