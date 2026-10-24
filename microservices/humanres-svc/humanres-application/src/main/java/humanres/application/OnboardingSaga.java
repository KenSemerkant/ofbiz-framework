package humanres.application;

import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import humanres.application.OnboardingSagaStore.SagaRow;

/**
 * P6-T3 onboarding orchestrator: step 1 grants the EMPLOYEE role via
 * party-svc, step 2 creates the employment + its outbox event. Every step
 * advances the persisted saga row through guarded markers, so a retry or
 * restart re-drive is a no-op for completed steps, and
 * {@link #resumePending()} re-drives incomplete sagas after a restart.
 */
public class OnboardingSaga {

    public static final String ONBOARDING_COMPLETED = "humanres.onboarding.completed.v1";

    /** Saga-created employments derive their id from the onboarding id. */
    public static String employmentIdFor(String onboardingId) {
        return "EMP-" + onboardingId;
    }

    private final OnboardingSagaStore sagas;
    private final PartyProjectionStore parties;
    private final EmplPositionStore positions;
    private final PartyRoleClient partyRoles;
    private final EmploymentService employments;
    private final OutboxWriter outbox;
    private final HumanresSerializer serializer;

    public OnboardingSaga(OnboardingSagaStore sagas, PartyProjectionStore parties,
            EmplPositionStore positions, PartyRoleClient partyRoles,
            EmploymentService employments, OutboxWriter outbox,
            HumanresSerializer serializer) {
        this.sagas = sagas;
        this.parties = parties;
        this.positions = positions;
        this.partyRoles = partyRoles;
        this.employments = employments;
        this.outbox = outbox;
        this.serializer = serializer;
    }

    public Optional<SagaRow> find(String onboardingId) {
        return sagas.find(onboardingId);
    }

    /**
     * @throws PartyNotFoundException unknown party (no saga row written)
     * @throws NoSuchElementException  unknown position (no saga row written)
     */
    public SagaRow start(String partyId, String emplPositionId) {
        if (parties.find(partyId).isEmpty()) {
            throw new PartyNotFoundException(partyId);
        }
        positions.find(emplPositionId)
            .orElseThrow(() -> new NoSuchElementException("no empl position " + emplPositionId));
        String onboardingId = "ONB-" + UUID.randomUUID();
        sagas.create(onboardingId, partyId, emplPositionId);
        return drive(onboardingId);
    }

    /** Re-drive from the persisted state; completed steps are no-ops. */
    public SagaRow retry(String onboardingId) {
        sagas.find(onboardingId)
            .orElseThrow(() -> new NoSuchElementException("no onboarding " + onboardingId));
        return drive(onboardingId);
    }

    /** Restart recovery (resumable AC): re-drive incomplete sagas. */
    public void resumePending() {
        for (String onboardingId : sagas.findIncompleteIds()) {
            try {
                drive(onboardingId);
            } catch (RuntimeException e) {
                sagas.markFailed(onboardingId, e.getMessage());
            }
        }
    }

    private SagaRow drive(String onboardingId) {
        try {
            addRoleStep(onboardingId);
            employmentStep(onboardingId);
        } catch (RuntimeException e) {
            sagas.markFailed(onboardingId, e.getMessage());
        }
        return sagas.find(onboardingId).orElseThrow();
    }

    private void addRoleStep(String onboardingId) {
        SagaRow saga = sagas.find(onboardingId).orElseThrow();
        if (saga.status() == OnboardingSagaStore.Status.ROLE_ADDED
                || saga.status() == OnboardingSagaStore.Status.COMPLETED) {
            return; // role step already done — redelivery no-op
        }
        // FAILED re-entry re-grants: party roles are a set, so a grant that
        // landed before a crash is idempotent (same reasoning as order-svc
        // re-reserving after a mid-step crash)
        partyRoles.addEmployeeRole(saga.partyId());
        sagas.markRoleAdded(onboardingId);
    }

    private void employmentStep(String onboardingId) {
        SagaRow saga = sagas.find(onboardingId).orElseThrow();
        if (saga.status() != OnboardingSagaStore.Status.ROLE_ADDED) {
            return; // not at the employment point (or already past) — no-op
        }
        String employmentId = employmentIdFor(onboardingId);
        try {
            employments.create(employmentId, saga.partyId(), saga.emplPositionId(),
                LocalDate.now(), null);
        } catch (DuplicateEmploymentException e) {
            // step already done (crash between create and markCompleted)
        }
        if (sagas.markCompleted(onboardingId)) {
            outbox.append(ONBOARDING_COMPLETED, onboardingId,
                serializer.serializeOnboardingCompleted(saga, employmentId));
        }
    }
}
