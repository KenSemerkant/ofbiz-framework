package manufacturing.adapters.rest.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BomRepository extends JpaRepository<BomEntity, String> {
}
