package search.adapters.rest.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryNameRepository extends JpaRepository<CategoryNameEntity, String> {
}
