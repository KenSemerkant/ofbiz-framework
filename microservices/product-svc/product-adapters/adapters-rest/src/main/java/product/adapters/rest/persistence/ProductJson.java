package product.adapters.rest.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import product.domain.ProductAssoc;
import tools.jackson.databind.ObjectMapper;

/**
 * JSON mapping for the inline categories/assocs columns and the event payload
 * (same shape both places, P3-F1-S1).
 */
public final class ProductJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record AssocDto(String productIdTo, String productAssocTypeId, Instant fromDate,
            Instant thruDate) {
    }

    private ProductJson() {
    }

    public static String serializeCategories(Collection<String> categoryIds) {
        return MAPPER.writeValueAsString(categoryIds == null ? List.of() : categoryIds);
    }

    public static List<String> deserializeCategories(String json) {
        return MAPPER.readValue(json,
            MAPPER.getTypeFactory().constructCollectionType(ArrayList.class, String.class));
    }

    public static String serializeAssocs(List<ProductAssoc> assocs) {
        return MAPPER.writeValueAsString(toDtos(assocs));
    }

    /** Assocs as a JSON tree for embedding in event payloads. */
    public static tools.jackson.databind.JsonNode serializeAssocNode(List<ProductAssoc> assocs) {
        return MAPPER.valueToTree(toDtos(assocs));
    }

    public static List<ProductAssoc> deserializeAssocs(String json) {
        List<AssocDto> dtos = MAPPER.readValue(json,
            MAPPER.getTypeFactory().constructCollectionType(ArrayList.class, AssocDto.class));
        return dtos.stream()
            .map(dto -> new ProductAssoc(dto.productIdTo(), dto.productAssocTypeId(),
                dto.fromDate(), dto.thruDate()))
            .toList();
    }

    private static List<AssocDto> toDtos(List<ProductAssoc> assocs) {
        return assocs.stream()
            .map(assoc -> new AssocDto(assoc.productIdTo(), assoc.productAssocTypeId(),
                assoc.fromDate(), assoc.thruDate()))
            .toList();
    }
}
