package finance.adapters.rest.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import finance.application.PeriodStore;
import finance.domain.Period;

/** JPA adapter for the PeriodStore port. */
@Repository
public class JpaPeriodStore implements PeriodStore {

    private final PeriodRepository periods;

    public JpaPeriodStore(PeriodRepository periods) {
        this.periods = periods;
    }

    @Override
    public Optional<Period> find(String periodId) {
        return periods.findById(periodId).map(this::toDomain);
    }

    @Override
    public void save(Period period) {
        periods.save(new PeriodEntity(period.periodId(), period.fromDate(), period.thruDate(),
            period.isClosed()));
    }

    @Override
    public List<Period> allOpen() {
        return periods.findByClosedFalseOrderByPeriodIdAsc().stream().map(this::toDomain)
            .toList();
    }

    private Period toDomain(PeriodEntity entity) {
        return Period.rehydrate(entity.getPeriodId(), entity.getFromDate(),
            entity.getThruDate(), entity.isClosed());
    }
}
