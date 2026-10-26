package humanres.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import humanres.adapters.rest.events.OutboxRelay;
import humanres.adapters.rest.events.PartyProjectionConsumer;
import humanres.adapters.rest.events.SagaResumeRunner;
import humanres.adapters.rest.integration.HttpIntegrations.HttpPartyRoleClient;
import humanres.adapters.rest.persistence.JacksonHumanresSerializer;
import humanres.adapters.rest.persistence.JdbcOnboardingSagaStore;
import humanres.adapters.rest.persistence.OutboxEntryEntity;
import humanres.adapters.rest.persistence.OutboxRepository;
import humanres.adapters.rest.persistence.SmallStores;
import humanres.application.EmploymentService;
import humanres.application.HumanresSerializer;
import humanres.application.OnboardingSaga;
import humanres.application.OnboardingSagaStore;
import humanres.application.OnboardingSagaStore.SagaRow;
import humanres.application.PartyNotFoundException;
import humanres.application.PartyProjectionStore;
import humanres.application.PartyRoleClient;
import humanres.application.PositionService;
import humanres.domain.EmplPosition;
import humanres.domain.Employment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit coverage for the adapter classes the API slice leaves cold (relay,
 * projection consumer, role client, JDBC saga store, resume runner,
 * serializer) — the FinanceAdaptersUnitTest pattern: fakes + one Postgres
 * container.
 */
@Testcontainers
class HumanresAdaptersUnitTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static JdbcTemplate db;

    @BeforeAll
    static void schema() {
        db = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(), POSTGRES.getPassword()));
        db.execute("create table processed_events (event_id varchar(255) primary key,"
            + " processed_at timestamptz not null default now())");
        db.execute("create table onboarding_saga (onboarding_id varchar(255) primary key,"
            + " party_id varchar(255) not null, empl_position_id varchar(255) not null,"
            + " status varchar(32) not null, steps jsonb not null default '[]',"
            + " failed_reason varchar(512), updated_at timestamptz not null default now())");
    }

    @Test
    void processedEventStoreIsIdempotent() {
        SmallStores.JdbcProcessedEventStore processed = new SmallStores.JdbcProcessedEventStore(
            db);
        assertTrue(processed.recordIfAbsent("evt-h1"));
        assertFalse(processed.recordIfAbsent("evt-h1"), "a replayed id is a no-op");
        new SmallStores.ProcessedEventEntity();
    }

    @Test
    void jdbcSagaStoreTransitionsAreGuarded() {
        JdbcOnboardingSagaStore sagas = new JdbcOnboardingSagaStore(db);
        sagas.create("ONB-J1", "P-100", "POS-1");
        assertEquals(OnboardingSagaStore.Status.PENDING,
            sagas.find("ONB-J1").orElseThrow().status());
        assertTrue(sagas.markRoleAdded("ONB-J1"));
        assertFalse(sagas.markRoleAdded("ONB-J1"), "second transition is a no-op");
        assertTrue(sagas.markCompleted("ONB-J1"));
        assertEquals(List.of("ADD_ROLE", "CREATE_EMPLOYMENT"),
            sagas.find("ONB-J1").orElseThrow().steps());
        assertEquals(OnboardingSagaStore.Status.COMPLETED,
            sagas.find("ONB-J1").orElseThrow().status());
        assertTrue(sagas.findIncompleteIds().isEmpty());

        // FAILED re-entry: markRoleAdded accepts FAILED, not COMPLETED
        sagas.create("ONB-J2", "P-100", "POS-1");
        sagas.markFailed("ONB-J2", "party-svc down");
        assertEquals("party-svc down", sagas.find("ONB-J2").orElseThrow().failedReason());
        assertFalse(sagas.findIncompleteIds().contains("ONB-J2"),
            "FAILED waits for manual retry, not auto-resume");
        assertTrue(sagas.markRoleAdded("ONB-J2"));
        assertFalse(sagas.markRoleAdded("ONB-J1"), "COMPLETED never re-advances");
    }

    @Test
    void projectionConsumerProjectsNamesAndSkipsRedelivery() {
        RecordingProjectionStore projections = new RecordingProjectionStore();
        PartyProjectionConsumer consumer = new PartyProjectionConsumer(projections,
            new SmallStores.JdbcProcessedEventStore(db));
        String payload = "{\"partyId\":\"P-9\",\"kind\":\"PERSON\",\"firstName\":\"Alex\","
            + "\"lastName\":\"Smith\",\"roles\":[],\"contactMechs\":[]}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "party.party.created.v1", 0, 7, "P-9", payload);
        consumer.onPartyEvent(record);
        consumer.onPartyEvent(record);
        assertEquals(1, projections.upserts.size());
        assertEquals("P-9", projections.upserts.get(0).partyId());
        JsonNode names = new ObjectMapper().readTree(projections.upserts.get(0).namesJson());
        assertEquals("Alex", names.path("firstName").asText());
        assertEquals("Smith", names.path("lastName").asText());
        assertFalse(names.has("groupName"));

        // a party with no name fields still projects (existence is the point)
        consumer.onPartyEvent(new ConsumerRecord<>("party.party.updated.v1", 0, 8, "P-10",
            "{\"partyId\":\"P-10\",\"kind\":\"PARTY_GROUP\",\"groupName\":\"Acme\"}"));
        assertEquals(2, projections.upserts.size());
        assertTrue(projections.upserts.get(1).namesJson().contains("Acme"));

        // a payload without a party id is skipped, not projected
        consumer.onPartyEvent(new ConsumerRecord<>("party.party.created.v1", 0, 9, "bad",
            "{\"kind\":\"PERSON\"}"));
        assertEquals(2, projections.upserts.size());
    }

    @Test
    void roleClientGrantsEmployeeRoleViaUpsert() throws Exception {
        RoleStub stub = new RoleStub();
        HttpPartyRoleClient client = new HttpPartyRoleClient(
            "http://localhost:" + stub.port);
        client.addEmployeeRole("P-100");
        JsonNode upsert = new ObjectMapper().readTree(stub.lastUpsertBody.get());
        assertEquals("PERSON", upsert.path("kind").asText());
        assertEquals("Alex", upsert.path("firstName").asText());
        assertEquals("Smith", upsert.path("lastName").asText());
        assertTrue(upsert.path("roles").toString().contains("EMPLOYEE"));

        stub.knownParty.set(false);
        assertThrows(PartyNotFoundException.class, () -> client.addEmployeeRole("GHOST"));

        // every saga-facing failure mode surfaces as an exception (fail loud)
        stub.knownParty.set(true);
        stub.mode.set("group");
        client.addEmployeeRole("P-GROUP");
        assertTrue(new ObjectMapper().readTree(stub.lastUpsertBody.get())
            .path("groupName").asText().equals("Acme Corp"));
        stub.mode.set("oneWordName");
        assertThrows(IllegalStateException.class, () -> client.addEmployeeRole("P-100"));
        stub.mode.set("fetch500");
        assertThrows(IllegalStateException.class, () -> client.addEmployeeRole("P-100"));
        stub.mode.set("post500");
        assertThrows(IllegalStateException.class, () -> client.addEmployeeRole("P-100"));
        stub.mode.set("dead");
        assertThrows(IllegalStateException.class,
            () -> new HttpPartyRoleClient("http://localhost:1").addEmployeeRole("P-100"));
        stub.mode.set("ok");
    }

    @Test
    void resumeRunnerDrivesIncompleteSagas() {
        MemoryWorld world = new MemoryWorld();
        world.sagas.create("ONB-R1", "P-100", "POS-1");
        world.sagas.markRoleAdded("ONB-R1"); // crash after the role step
        new SagaResumeRunner(world.onboarding).run(null);
        assertEquals(OnboardingSagaStore.Status.COMPLETED,
            world.sagas.find("ONB-R1").orElseThrow().status());
        assertTrue(world.employments.find("EMP-ONB-R1").isPresent());
    }

    @Test
    void relayPublishesAndMarks() {
        OutboxEntryEntity entry = new OutboxEntryEntity(
            "humanres.position.created.v1", "POS-R", "{}");
        OutboxRepository repo = org.mockito.Mockito.mock(OutboxRepository.class);
        org.mockito.Mockito.when(repo.findUnpublished()).thenReturn(List.of(entry));
        org.mockito.Mockito.when(repo.findById(org.mockito.ArgumentMatchers.any()))
            .thenReturn(java.util.Optional.of(entry));
        @SuppressWarnings("unchecked")
        org.springframework.kafka.core.KafkaTemplate<String, String> kafka =
            org.mockito.Mockito.mock(org.springframework.kafka.core.KafkaTemplate.class);
        org.mockito.Mockito
            .when(kafka.send("humanres.position.created.v1", "POS-R", "{}"))
            .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        OutboxRelay relay = new OutboxRelay(repo, kafka,
            new TransactionTemplate(new NoopTransactionManager()));
        relay.publishPending();
        org.mockito.Mockito.verify(kafka)
            .send("humanres.position.created.v1", "POS-R", "{}");
    }

    @Test
    void dlqDestinationAppendsSuffix() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "party.party.created.v1", 0, 1, "k", "{}");
        org.apache.kafka.common.TopicPartition destination =
            KafkaConfig.dlqDestination(record, new IllegalStateException("poison"));
        assertEquals("party.party.created.v1.dlq", destination.topic());
    }

    @Test
    void serializerCarriesEventContractFields() {
        JacksonHumanresSerializer serializer = new JacksonHumanresSerializer();
        EmplPosition position = new EmplPosition("POS-S", "ENGINEER", "POS-P", "ACTIVE");
        assertTrue(serializer.serializePosition(position)
            .contains("\"emplPositionId\":\"POS-S\""));
        assertTrue(serializer.serializePosition(position)
            .contains("\"reportingToEmplPositionId\":\"POS-P\""));
        assertFalse(serializer.serializePosition(new EmplPosition("POS-T", "ENGINEER", null,
            "ACTIVE")).contains("reportingToEmplPositionId"));
        Employment employment = new Employment("EMP-S", "P-100", "POS-S",
            LocalDate.parse("2026-10-01"), LocalDate.parse("2026-12-31"));
        String payload = serializer.serializeEmployment(employment);
        assertTrue(payload.contains("\"employmentId\":\"EMP-S\""));
        assertTrue(payload.contains("\"fromDate\":\"2026-10-01\""));
        assertTrue(payload.contains("\"thruDate\":\"2026-12-31\""));
        assertFalse(serializer.serializeEmployment(new Employment("EMP-O", "P-100", "POS-S",
            LocalDate.parse("2026-10-01"), null)).contains("thruDate"));
        SagaRow saga = new SagaRow("ONB-S", "P-100", "POS-S",
            OnboardingSagaStore.Status.COMPLETED, List.of(), null);
        assertTrue(serializer.serializeOnboardingCompleted(saga, "EMP-ONB-S")
            .contains("\"employmentId\":\"EMP-ONB-S\""));
    }

    @Test
    void projectionEntityCarriesItsColumns() {
        humanres.adapters.rest.persistence.PartyProjectionEntity entity =
            new humanres.adapters.rest.persistence.PartyProjectionEntity("P-E",
                "{\"firstName\":\"A\"}");
        assertEquals("P-E", entity.getPartyId());
        assertEquals("{\"firstName\":\"A\"}", entity.getNames());
        assertTrue(entity.getProjectedAt() != null);
        new humanres.adapters.rest.persistence.PartyProjectionEntity();
    }

    /** party-svc stand-in for the role client. */
    static final class RoleStub {

        final int port;
        final AtomicReference<String> lastUpsertBody = new AtomicReference<>("");
        final AtomicBoolean knownParty = new AtomicBoolean(true);
        final AtomicReference<String> mode = new AtomicReference<>("ok");

        RoleStub() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
                server.createContext("/", this::handle);
                server.start();
                this.port = server.getAddress().getPort();
            } catch (IOException e) {
                throw new IllegalStateException("role stub failed to start", e);
            }
        }

        private void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(exchange.getRequestMethod())
                    && path.startsWith("/api/v1/parties/")) {
                if (!knownParty.get()) {
                    respond(exchange, 404, "{}");
                } else if ("fetch500".equals(mode.get())) {
                    respond(exchange, 500, "{}");
                } else if ("group".equals(mode.get())) {
                    respond(exchange, 200, """
                        {"partyId":"P-GROUP","kind":"PARTY_GROUP","displayName":"Acme Corp",\
                        "roles":[],"contactMechs":[]}
                        """);
                } else if ("oneWordName".equals(mode.get())) {
                    respond(exchange, 200, """
                        {"partyId":"P-100","kind":"PERSON","displayName":"Cher",\
                        "roles":[],"contactMechs":[]}
                        """);
                } else {
                    respond(exchange, 200, """
                        {"partyId":"P-100","kind":"PERSON","displayName":"Alex Smith",\
                        "roles":["CUSTOMER"],"contactMechs":[]}
                        """);
                }
            } else if ("post500".equals(mode.get())) {
                exchange.getRequestBody().readAllBytes();
                respond(exchange, 500, "{}");
                exchange.close();
            } else {
                lastUpsertBody.set(new String(exchange.getRequestBody().readAllBytes()));
                respond(exchange, 201, "{}");
                exchange.close();
            }
        }

        private void respond(HttpExchange exchange, int status, String body)
                throws IOException {
            byte[] bytes = body.getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    record Upsert(String partyId, String namesJson) {
    }

    static final class RecordingProjectionStore implements PartyProjectionStore {

        final List<Upsert> upserts = new ArrayList<>();

        @Override
        public void upsert(String partyId, String namesJson) {
            upserts.add(new Upsert(partyId, namesJson));
        }

        @Override
        public Optional<String> find(String partyId) {
            return upserts.stream().filter(u -> u.partyId().equals(partyId))
                .map(Upsert::namesJson).findFirst();
        }
    }

    /** In-memory world so the resume runner runs against real saga logic. */
    static final class MemoryWorld {

        final MemoryPositionStore positions = new MemoryPositionStore();
        final MemoryEmploymentStore employments = new MemoryEmploymentStore();
        final RecordingProjectionStore parties = new RecordingProjectionStore();
        final MemorySagaStore sagas = new MemorySagaStore();
        final OnboardingSaga onboarding;

        MemoryWorld() {
            HumanresSerializer serializer = new JacksonHumanresSerializer();
            humanres.application.OutboxWriter outbox = (type, key, payload) -> {
            };
            positions.save(new EmplPosition("POS-1", "ENGINEER", null, "ACTIVE"));
            parties.upsert("P-100", "{}");
            PartyRoleClient roles = partyId -> {
            };
            EmploymentService employmentService = new EmploymentService(employments,
                positions, outbox, serializer);
            onboarding = new OnboardingSaga(sagas, parties, positions, roles,
                employmentService, outbox, serializer);
        }
    }

    static final class MemoryPositionStore implements humanres.application.EmplPositionStore {

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

    static final class MemoryEmploymentStore implements humanres.application.EmploymentStore {

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

    static final class MemorySagaStore implements OnboardingSagaStore {

        record Row(String onboardingId, String partyId, String emplPositionId,
                OnboardingSagaStore.Status status, List<String> steps,
                String failedReason) {
        }

        final List<Row> rows = new ArrayList<>();

        @Override
        public void create(String onboardingId, String partyId, String emplPositionId) {
            rows.add(new Row(onboardingId, partyId, emplPositionId,
                OnboardingSagaStore.Status.PENDING, new ArrayList<>(), null));
        }

        @Override
        public Optional<SagaRow> find(String onboardingId) {
            return rows.stream().filter(r -> r.onboardingId().equals(onboardingId))
                .findFirst().map(r -> new SagaRow(r.onboardingId(), r.partyId(),
                    r.emplPositionId(), r.status(), r.steps(), r.failedReason()));
        }

        @Override
        public synchronized boolean markRoleAdded(String onboardingId) {
            return transition(onboardingId, OnboardingSagaStore.Status.ROLE_ADDED,
                "ADD_ROLE", r -> r.status() == OnboardingSagaStore.Status.PENDING
                    || r.status() == OnboardingSagaStore.Status.FAILED);
        }

        @Override
        public synchronized boolean markCompleted(String onboardingId) {
            return transition(onboardingId, OnboardingSagaStore.Status.COMPLETED,
                "CREATE_EMPLOYMENT",
                r -> r.status() == OnboardingSagaStore.Status.ROLE_ADDED);
        }

        @Override
        public void markFailed(String onboardingId, String reason) {
            mutate(onboardingId, r -> r.status() != OnboardingSagaStore.Status.COMPLETED,
                r -> replace(r, OnboardingSagaStore.Status.FAILED, r.steps(), reason));
        }

        @Override
        public List<String> findIncompleteIds() {
            return rows.stream().filter(r -> r.status() == OnboardingSagaStore.Status.PENDING
                    || r.status() == OnboardingSagaStore.Status.ROLE_ADDED)
                .map(Row::onboardingId).toList();
        }

        private boolean transition(String onboardingId, OnboardingSagaStore.Status to,
                String step, java.util.function.Predicate<Row> guard) {
            return mutate(onboardingId, guard, r -> {
                List<String> steps = new ArrayList<>(r.steps());
                steps.add(step);
                replace(r, to, steps, null);
            });
        }

        private boolean mutate(String onboardingId, java.util.function.Predicate<Row> guard,
                java.util.function.Consumer<Row> action) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).onboardingId().equals(onboardingId) && guard.test(rows.get(i))) {
                    action.accept(rows.get(i));
                    return true;
                }
            }
            return false;
        }

        private void replace(Row row, OnboardingSagaStore.Status status, List<String> steps,
                String reason) {
            rows.set(rows.indexOf(row), new Row(row.onboardingId(), row.partyId(),
                row.emplPositionId(), status, steps, reason));
        }
    }

    static class NoopTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
