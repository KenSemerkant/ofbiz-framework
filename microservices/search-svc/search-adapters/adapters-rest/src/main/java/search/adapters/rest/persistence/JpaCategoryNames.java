package search.adapters.rest.persistence;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Repository;
import search.application.CategoryNames;

@Repository
public class JpaCategoryNames implements CategoryNames {

    private final CategoryNameRepository categories;

    public JpaCategoryNames(CategoryNameRepository categories) {
        this.categories = categories;
    }

    @Override
    public void save(String categoryId, String name) {
        categories.findById(categoryId)
            .ifPresentOrElse(entity -> entity.rename(name),
                () -> categories.save(new CategoryNameEntity(categoryId, name)));
    }

    @Override
    public Map<String, String> resolve(Collection<String> categoryIds) {
        Map<String, String> names = new LinkedHashMap<>();
        for (CategoryNameEntity entity : categories.findAllById(categoryIds)) {
            names.put(entity.getProductCategoryId(), entity.getCategoryName());
        }
        return names;
    }
}
