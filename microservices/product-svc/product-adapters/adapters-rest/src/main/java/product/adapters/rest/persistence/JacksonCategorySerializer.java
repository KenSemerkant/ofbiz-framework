package product.adapters.rest.persistence;

import java.util.LinkedHashMap;
import product.application.CategorySerializer;
import product.domain.Category;

/** Category event payload shape (quality review: Jackson-escaped, not hand-built). */
public class JacksonCategorySerializer implements CategorySerializer {

    private final tools.jackson.databind.ObjectMapper mapper =
        new tools.jackson.databind.ObjectMapper();

    @Override
    public String serialize(Category category) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("productCategoryId", category.productCategoryId());
        fields.put("categoryName", category.categoryName());
        fields.put("primaryParentCategoryId", category.primaryParentCategoryId());
        return mapper.writeValueAsString(fields);
    }
}
