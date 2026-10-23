package finance.application;

import java.util.List;
import java.util.Optional;
import finance.domain.Period;

/** Persistence port for fiscal periods. */
public interface PeriodStore {

    Optional<Period> find(String periodId);

    void save(Period period);

    /** Auto-posting target candidates, open periods only. */
    List<Period> allOpen();
}
