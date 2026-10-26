package humanres.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import humanres.application.OnboardingSagaStore.SagaRow;
import humanres.domain.EmplPosition;
import humanres.domain.Employment;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * P6-T3 use-case suite: position/employment CRUD with typed conflicts and
 * outbox events, and the onboarding saga — guarded step markers, retry
 * after a failed role grant, resume after a restart, and no duplicate
 * events on re-drive.
 */
class HumanresApplicationSuiteTest {

    private final FakeStores stores = new FakeStores();
    private final List<String> outbox = new ArrayList<>();
    private final FlakyRoleClient partyRoles = new FlakyRoleClient();
    private final PositionService positions;
    private final EmploymentService employments;
    private final OnboardingSaga onboarding;

    HumanresApplicationSuiteTest() {
        HumanresSerializer serializer = new StubSerializer();
        OutboxWriter writer = (type, key, payload) -> outbox.add(type + ":" + key);
        positions = new PositionService(stores.positions, writer, serializer);
        employments = new EmploymentService(stores.employments, stores.positions, writer,
            serializer);
        onboarding = new OnboardingSaga(stores.sagas, stores.parties, stores.positions,
            partyRoles, employments, writer, serializer);
        stores.parties.upsert("P-100", "{\"firstName\":\"Alex\"}");
        stores.positions.save(new EmplPosition("POS-1", "ENGINEER", null, "ACTIVE"));
    }

    /** P9-F6: listings for the SPA HR area. */
    @Test
    void listingsReturnCreatedRowsSortedById() {
        positions.create("POS-L2", "MANAGER", null, "ACTIVE");
        positions.create("POS-L1", "WORKER", null, "ACTIVE");
        var ids = positions.all().stream().map(EmplPosition::emplPositionId).toList();
        assertTrue(ids.containsAll(java.util.List.of("POS-L1", "POS-L2")));
        assertEquals(ids, ids.stream().sorted().toList());
        assertTrue(employments.all().isEmpty());
    }

    @Test
    void positionCrudEmitsEventsAndRefusesDuplicates() {
        EmplPosition created = positions.create("POS-2", "MANAGER", "POS-1", "ACTIVE");
        assertEquals("POS-2", created.emplPositionId());
        assertTrue(outbox.contains("humanres.position.created.v1:POS-2"));
        positions.update("POS-2", "SENIOR_MANAGER", "POS-1", "ACTIVE");
        assertTrue(outbox.contains("humanres.position.updated.v1:POS-2"));
        assertEquals("SENIOR_MANAGER", positions.find("POS-2").orElseThrow()
            .emplPositionTypeId());
        assertThrows(DuplicatePositionException.class,
            () -> positions.create("POS-2", "MANAGER", null, "ACTIVE"));
        assertThrows(java.util.NoSuchElementException.class,
            () -> positions.update("POS-404", "MANAGER", null, "ACTIVE"));
        assertThrows(java.util.NoSuchElementException.class,
            () -> positions.create("POS-3", "MANAGER", "POS-404", "ACTIVE"));
    }

    @Test
    void employmentCreateValidatesPositionAndEmitsEvent() {
        Employment created = employments.create("EMP-1", "P-100", "POS-1",
            LocalDate.parse("2026-10-01"), null);
        assertEquals("EMP-1", created.employmentId());
        assertTrue(outbox.contains("humanres.employment.created.v1:EMP-1"));
        assertThrows(DuplicateEmploymentException.class,
            () -> employments.create("EMP-1", "P-100", "POS-1",
                LocalDate.parse("2026-10-01"), null));
        assertThrows(java.util.NoSuchElementException.class,
            () -> employments.create("EMP-2", "P-100", "POS-404",
                LocalDate.parse("2026-10-01"), null));
        employments.updateThruDate("EMP-1", LocalDate.parse("2026-12-31"));
        assertEquals(LocalDate.parse("2026-12-31"),
            employments.find("EMP-1").orElseThrow().thruDate());
        assertThrows(java.util.NoSuchElementException.class,
            () -> employments.updateThruDate("EMP-404", null));
    }

    @Test
    void onboardingHappyPathRunsBothStepsOnce() {
        SagaRow saga = onboarding.start("P-100", "POS-1");
        assertEquals(OnboardingSagaStore.Status.COMPLETED, saga.status());
        assertEquals(List.of("ADD_ROLE", "CREATE_EMPLOYMENT"), saga.steps());
        assertTrue(partyRoles.granted.contains("P-100"));
        String employmentId = OnboardingSaga.employmentIdFor(saga.onboardingId());
        assertTrue(stores.employments.find(employmentId).isPresent());
        assertTrue(outbox.contains("humanres.employment.created.v1:" + employmentId));
        assertTrue(outbox.contains("humanres.onboarding.completed.v1:" + saga.onboardingId()));
        // re-drive of a completed saga changes nothing
        SagaRow again = onboarding.retry(saga.onboardingId());
        assertEquals(OnboardingSagaStore.Status.COMPLETED, again.status());
        assertEquals(1, partyRoles.granted.size(), "role grant must not repeat");
        assertEquals(1, outbox.stream()
            .filter(e -> e.startsWith("humanres.onboarding.completed.v1")).count());
    }

    @Test
    void unknownPartyOrPositionRefusedBeforeAnySagaRow() {
        assertThrows(PartyNotFoundException.class, () -> onboarding.start("GHOST", "POS-1"));
        assertThrows(java.util.NoSuchElementException.class,
            () -> onboarding.start("P-100", "POS-404"));
        assertTrue(stores.sagas.rows.isEmpty());
    }

    @Test
    void failedRoleGrantMarksFailedAndRetryCompletes() {
        partyRoles.failNextCall = true;
        SagaRow saga = onboarding.start("P-100", "POS-1");
        assertEquals(OnboardingSagaStore.Status.FAILED, saga.status());
        assertNotNull(saga.failedReason());
        assertTrue(stores.employments.find(OnboardingSaga.employmentIdFor(saga.onboardingId()))
            .isEmpty(), "no employment before the role step lands");
        SagaRow retried = onboarding.retry(saga.onboardingId());
        assertEquals(OnboardingSagaStore.Status.COMPLETED, retried.status());
        assertEquals(List.of("ADD_ROLE", "CREATE_EMPLOYMENT"), retried.steps());
        assertTrue(stores.employments.find(OnboardingSaga.employmentIdFor(saga.onboardingId()))
            .isPresent());
    }

    @Test
    void resumePendingDrivesIncompleteSagasAfterRestart() {
        // simulate a crash after the role step: row stuck at ROLE_ADDED
        stores.sagas.create("ONB-STUCK", "P-100", "POS-1");
        stores.sagas.markRoleAdded("ONB-STUCK");
        onboarding.resumePending();
        SagaRow resumed = stores.sagas.find("ONB-STUCK").orElseThrow();
        assertEquals(OnboardingSagaStore.Status.COMPLETED, resumed.status());
        assertTrue(stores.employments.find("EMP-ONB-STUCK").isPresent());
        // a crashing neighbour parks as FAILED, never silently dropped
        partyRoles.failNextCall = true;
        stores.sagas.create("ONB-DEAD", "P-100", "POS-1");
        onboarding.resumePending();
        assertEquals(OnboardingSagaStore.Status.FAILED,
            stores.sagas.find("ONB-DEAD").orElseThrow().status());
    }

    @Test
    void findOnboardingUnknownIsEmpty() {
        assertTrue(onboarding.find("ONB-404").isEmpty());
        assertThrows(java.util.NoSuchElementException.class,
            () -> onboarding.retry("ONB-404"));
    }

    /** Serializer stub: payload identity is the adapters test's concern. */
    private static final class StubSerializer implements HumanresSerializer {

        @Override
        public String serializePosition(EmplPosition position) {
            return "position:" + position.emplPositionId();
        }

        @Override
        public String serializeEmployment(Employment employment) {
            return "employment:" + employment.employmentId();
        }

        @Override
        public String serializeOnboardingCompleted(SagaRow saga, String employmentId) {
            return "onboarding:" + saga.onboardingId();
        }
    }

    /** Role client that can fail once on demand (party-svc outage stand-in). */
    static final class FlakyRoleClient implements PartyRoleClient {

        final List<String> granted = new ArrayList<>();
        boolean failNextCall;

        @Override
        public void addEmployeeRole(String partyId) {
            if (failNextCall) {
                failNextCall = false;
                throw new IllegalStateException("party-svc unreachable");
            }
            granted.add(partyId);
        }
    }

    /** In-memory fakes for every store port. */
    private static final class FakeStores {
        final FakePositionStore positions = new FakePositionStore();
        final FakeEmploymentStore employments = new FakeEmploymentStore();
        final FakePartyProjectionStore parties = new FakePartyProjectionStore();
        final InMemorySagaStore sagas = new InMemorySagaStore();
    }

    static final class FakePositionStore implements EmplPositionStore {
        final List<EmplPosition> rows = new ArrayList<>();

        @Override
        public Optional<EmplPosition> find(String emplPositionId) {
            return rows.stream().filter(p -> p.emplPositionId().equals(emplPositionId))
                .findFirst();
        }

        @Override
        public List<EmplPosition> all() {
            return rows.stream()
                .sorted(java.util.Comparator.comparing(EmplPosition::emplPositionId)).toList();
        }

        @Override
        public void save(EmplPosition position) {
            rows.removeIf(p -> p.emplPositionId().equals(position.emplPositionId()));
            rows.add(position);
        }
    }

    static final class FakeEmploymentStore implements EmploymentStore {
        final List<Employment> rows = new ArrayList<>();

        @Override
        public Optional<Employment> find(String employmentId) {
            return rows.stream().filter(e -> e.employmentId().equals(employmentId))
                .findFirst();
        }

        @Override
        public List<Employment> all() {
            return rows.stream()
                .sorted(java.util.Comparator.comparing(Employment::employmentId)).toList();
        }

        @Override
        public void save(Employment employment) {
            rows.removeIf(e -> e.employmentId().equals(employment.employmentId()));
            rows.add(employment);
        }
    }

    static final class FakePartyProjectionStore implements PartyProjectionStore {
        final List<String> rows = new ArrayList<>();

        @Override
        public void upsert(String partyId, String namesJson) {
            rows.removeIf(partyId::equals);
            rows.add(partyId);
        }

        @Override
        public Optional<String> find(String partyId) {
            return rows.stream().filter(partyId::equals).findFirst();
        }
    }

    static final class InMemorySagaStore implements OnboardingSagaStore {
        final List<SagaRow> rows = new ArrayList<>();

        @Override
        public void create(String onboardingId, String partyId, String emplPositionId) {
            rows.add(new SagaRow(onboardingId, partyId, emplPositionId, Status.PENDING,
                new ArrayList<>(), null));
        }

        @Override
        public Optional<SagaRow> find(String onboardingId) {
            return rows.stream().filter(r -> r.onboardingId().equals(onboardingId))
                .findFirst();
        }

        @Override
        public synchronized boolean markRoleAdded(String onboardingId) {
            return transition(onboardingId, Status.ROLE_ADDED, "ADD_ROLE",
                r -> r.status() == Status.PENDING || r.status() == Status.FAILED);
        }

        @Override
        public synchronized boolean markCompleted(String onboardingId) {
            return transition(onboardingId, Status.COMPLETED, "CREATE_EMPLOYMENT",
                r -> r.status() == Status.ROLE_ADDED);
        }

        @Override
        public void markFailed(String onboardingId, String reason) {
            mutate(onboardingId, r -> r.status() != Status.COMPLETED, r -> replace(r,
                Status.FAILED, r.steps(), reason));
        }

        @Override
        public List<String> findIncompleteIds() {
            return rows.stream()
                .filter(r -> r.status() == Status.PENDING || r.status() == Status.ROLE_ADDED)
                .map(SagaRow::onboardingId).toList();
        }

        private boolean transition(String onboardingId, Status to, String step,
                java.util.function.Predicate<SagaRow> guard) {
            return mutate(onboardingId, guard, r -> {
                List<String> steps = new ArrayList<>(r.steps());
                steps.add(step);
                replace(r, to, steps, null);
            });
        }

        private boolean mutate(String onboardingId, java.util.function.Predicate<SagaRow> guard,
                java.util.function.Consumer<SagaRow> action) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).onboardingId().equals(onboardingId) && guard.test(rows.get(i))) {
                    action.accept(rows.get(i));
                    return true;
                }
            }
            return false;
        }

        private void replace(SagaRow row, Status status, List<String> steps, String reason) {
            rows.set(rows.indexOf(row), new SagaRow(row.onboardingId(), row.partyId(),
                row.emplPositionId(), status, steps, reason));
        }
    }
}
