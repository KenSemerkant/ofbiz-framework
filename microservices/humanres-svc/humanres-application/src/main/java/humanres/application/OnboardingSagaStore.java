package humanres.application;

import java.util.List;
import java.util.Optional;

/**
 * P6-T3 onboarding saga state: persisted per onboarding so the orchestrator
 * is resumable after a restart and idempotent under retry — every marker
 * below is a guarded transition; a second call is a no-op.
 */
public interface OnboardingSagaStore {

    enum Status { PENDING, ROLE_ADDED, COMPLETED, FAILED }

    /** steps is the completed-step log (ADD_ROLE, CREATE_EMPLOYMENT). */
    record SagaRow(String onboardingId, String partyId, String emplPositionId, Status status,
            List<String> steps, String failedReason) {
    }

    void create(String onboardingId, String partyId, String emplPositionId);

    Optional<SagaRow> find(String onboardingId);

    /** Guarded PENDING/FAILED→ROLE_ADDED; appends ADD_ROLE to the steps. */
    boolean markRoleAdded(String onboardingId);

    /** Guarded ROLE_ADDED→COMPLETED; appends CREATE_EMPLOYMENT to the steps. */
    boolean markCompleted(String onboardingId);

    void markFailed(String onboardingId, String reason);

    /** Onboardings that never reached COMPLETED/FAILED (restart scan). */
    List<String> findIncompleteIds();
}
