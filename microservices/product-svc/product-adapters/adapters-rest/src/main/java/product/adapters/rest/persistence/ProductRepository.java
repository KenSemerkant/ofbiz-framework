package product.adapters.rest.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, String> {

    /**
     * Membership probe over the inline categories JSON — the quoted element
     * match (`%"CAT"%`) cannot match a substring of a longer id.
     */
    boolean existsByCategoriesContaining(String categories);
}
