package product.adapters.rest.persistence;

import java.util.LinkedHashMap;
import product.application.ProductSerializer;
import product.domain.Product;
import product.domain.ProductPrice;

/**
 * Event payload shapes consumed by search-svc and future order-svc readers.
 * Every payload goes through Jackson so user-supplied quotes/backslashes are
 * escaped (quality review: no hand-built JSON).
 */
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

    @Override
    public String serializeDeleted(String productId) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("productId", productId);
        return mapper.writeValueAsString(fields);
    }

    @Override
    public String serializePriceChange(String productId, ProductPrice price, boolean removed) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("productId", productId);
        fields.put("productPriceTypeId", price.productPriceTypeId());
        fields.put("currencyUomId", price.currencyUomId());
        fields.put("price", price.price());
        fields.put("removed", removed);
        return mapper.writeValueAsString(fields);
    }
}
