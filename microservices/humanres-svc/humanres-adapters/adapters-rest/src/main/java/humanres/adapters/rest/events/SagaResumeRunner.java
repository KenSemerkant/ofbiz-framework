package humanres.adapters.rest.events;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import humanres.application.OnboardingSaga;

/**
 * P6-T3 resume AC: on startup, re-drive every onboarding saga that never
 * reached COMPLETED/FAILED — the persisted markers make each re-drive
 * idempotent.
 */
@Component
@ConditionalOnProperty(name = "humanres.saga.enabled", havingValue = "true")
public class SagaResumeRunner implements ApplicationRunner {

    private final OnboardingSaga onboarding;

    public SagaResumeRunner(OnboardingSaga onboarding) {
        this.onboarding = onboarding;
    }

    @Override
    public void run(ApplicationArguments args) {
        onboarding.resumePending();
    }
}
