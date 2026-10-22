package product.adapters.rest.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<StoreEntity, String> {
}
