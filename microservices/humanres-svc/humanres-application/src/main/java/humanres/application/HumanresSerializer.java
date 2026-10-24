package humanres.application;

import humanres.domain.EmplPosition;
import humanres.domain.Employment;

/** Event-payload port: the application layer never touches Jackson. */
public interface HumanresSerializer {

    String serializePosition(EmplPosition position);

    String serializeEmployment(Employment employment);

    String serializeOnboardingCompleted(OnboardingSagaStore.SagaRow saga, String employmentId);
}
