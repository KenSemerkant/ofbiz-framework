package finance.application;

import finance.domain.GlTransaction;
import finance.domain.Period;

/** Event-payload port: the application layer never touches Jackson. */
public interface FinanceSerializer {

    String serializeTransaction(GlTransaction transaction);

    String serializePeriodClosed(Period period);
}
