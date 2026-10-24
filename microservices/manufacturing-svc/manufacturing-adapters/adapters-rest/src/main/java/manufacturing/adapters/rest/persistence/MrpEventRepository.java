package manufacturing.adapters.rest.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MrpEventRepository extends JpaRepository<MrpEventEntity, String> {
}
