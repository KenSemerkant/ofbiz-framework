package order.adapters.rest.events;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import order.application.OrderEcaService;

/**
 * P4-F2-S1 resume AC: on startup, re-drive every saga that never reached
 * DONE/COMPENSATED — the persisted markers make each re-drive idempotent.
 */
@Component
@ConditionalOnProperty(name = "order.eca.enabled", havingValue = "true")
public class SagaResumeRunner implements ApplicationRunner {

    private final OrderEcaService eca;

    public SagaResumeRunner(OrderEcaService eca) {
        this.eca = eca;
    }

    @Override
    public void run(ApplicationArguments args) {
        eca.resumePending();
    }
}
