package manufacturing.adapters.rest.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutingRepository extends JpaRepository<RoutingEntity, String> {
}
