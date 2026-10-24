package manufacturing.adapters.rest.persistence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import manufacturing.domain.Bom;
import tools.jackson.databind.ObjectMapper;

/** JSON mapping for the inline BOM components. */
final class BomComponentJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    record ComponentDto(String productId, BigDecimal quantity, BigDecimal scrapPct) {
    }

    public static String serialize(List<Bom.Component> components) {
        return MAPPER.writeValueAsString(components.stream()
            .map(c -> new ComponentDto(c.productId(), c.quantity(), c.scrapPct()))
            .toList());
    }

    public static List<Bom.Component> deserialize(String json) {
        List<ComponentDto> dtos = MAPPER.readValue(json,
            MAPPER.getTypeFactory().constructCollectionType(ArrayList.class, ComponentDto.class));
        return dtos.stream()
            .map(dto -> new Bom.Component(dto.productId(), dto.quantity(), dto.scrapPct()))
            .toList();
    }

    private BomComponentJson() {
    }
}
