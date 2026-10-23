package finance.adapters.rest.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PeriodRepository extends JpaRepository<PeriodEntity, String> {

    List<PeriodEntity> findByClosedFalseOrderByPeriodIdAsc();
}
