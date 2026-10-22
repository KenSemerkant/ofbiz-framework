package product.adapters.rest.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;
import product.application.CategoryService;
import product.domain.Category;
import product.domain.CategoryNode;

/** North-south REST edge (ADR-010): only the gateway routes here. */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    public record UpsertCategoryRequest(String productCategoryId,
            String primaryParentCategoryId, String categoryName) {
    }

    public record CategoryResponse(String productCategoryId, String primaryParentCategoryId,
            String categoryName) {
    }

    public record CategoryTreeResponse(String productCategoryId, String primaryParentCategoryId,
            String categoryName, List<CategoryTreeResponse> children) {
    }

    public record ConflictResponse(String error) {
    }

    /** Adapter-layer transaction boundary (ADR-009, spec-review fix). */
    @PostMapping
    @Transactional
    ResponseEntity<CategoryResponse> upsert(@RequestBody UpsertCategoryRequest request) {
        Category category;
        try {
            category = new Category(request.productCategoryId(),
                request.primaryParentCategoryId(), request.categoryName());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
        categoryService.upsert(category);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(category));
    }

    @GetMapping("/{productCategoryId}")
    ResponseEntity<CategoryResponse> get(@PathVariable String productCategoryId) {
        return categoryService.get(productCategoryId)
            .map(this::toResponse)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Category tree query (P3-F1-S1 AC). */
    @GetMapping("/{productCategoryId}/tree")
    ResponseEntity<CategoryTreeResponse> tree(@PathVariable String productCategoryId) {
        return categoryService.tree(productCategoryId)
            .map(this::toTreeResponse)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Delete (spec-review fix): only a leaf without member products. */
    @DeleteMapping("/{productCategoryId}")
    ResponseEntity<?> delete(@PathVariable String productCategoryId) {
        return switch (categoryService.delete(productCategoryId)) {
            case DELETED -> ResponseEntity.noContent().build();
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case NOT_LEAF -> ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ConflictResponse(productCategoryId
                    + " is not a leaf: it has child categories or member products"));
        };
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.productCategoryId(),
            category.primaryParentCategoryId(), category.categoryName());
    }

    private CategoryTreeResponse toTreeResponse(CategoryNode node) {
        return new CategoryTreeResponse(node.category().productCategoryId(),
            node.category().primaryParentCategoryId(), node.category().categoryName(),
            node.children().stream().map(this::toTreeResponse).toList());
    }
}
