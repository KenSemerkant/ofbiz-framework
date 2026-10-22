package product.adapters.rest.persistence;

import java.util.LinkedHashMap;
import product.application.ProductSerializer;
import product.domain.Product;

/** Event payload shape consumed by search-svc and future order-svc readers. */
public class JacksonProductSerializer implements ProductSerializer {

    private final tools.jackson.databind.ObjectMapper mapper =
        new tools.jackson.databind.ObjectMapper();

    @Override
    public String serialize(Product product) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("productId", product.productId());
        fields.put("productTypeId", product.productTypeId());
        fields.put("internalName", product.internalName());
        fields.put("productName", product.productName());
        fields.put("categories", product.categoryIds());
        fields.put("associations", ProductJson.serializeAssocNode(product.associations()));
        fields.put("prices", ProductJson.serializePriceNode(product.prices()));
        return mapper.writeValueAsString(fields);
    }
}
