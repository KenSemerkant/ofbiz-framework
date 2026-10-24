package marketing.adapters.rest.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackingCodeRepository extends JpaRepository<TrackingCodeEntity, String> {

    List<TrackingCodeEntity> findAllByOrderByTrackingCodeIdAsc();
}
