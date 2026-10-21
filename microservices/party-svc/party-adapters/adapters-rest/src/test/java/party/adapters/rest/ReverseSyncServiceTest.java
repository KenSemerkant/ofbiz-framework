package party.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import party.application.MonolithSyncWriter;
import party.application.ReverseSyncService;
import party.adapters.rest.persistence.JdbcMonolithSyncWriter;

/**
 * P2-F2-S3 AC: `party.party.updated` applies back to the monolith table;
 * conflict policy new-service-wins proven (update overwrites, revoked role
 * disappears).
 */
class ReverseSyncServiceTest {

    private static final PostgreSQLContainer<?> MONOLITH =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ofbiz").withUsername("ofbiz").withPassword("ofbiz");

    private static JdbcTemplate monolith;
    private static ReverseSyncService service;

    @BeforeAll
    static void startMonolith() {
        MONOLITH.start();
        DataSource ds = DataSourceBuilder.create().url(MONOLITH.getJdbcUrl())
            .username(MONOLITH.getUsername()).password(MONOLITH.getPassword()).build();
        monolith = new JdbcTemplate(ds);
        monolith.execute("""
            CREATE TABLE "PARTY" ("PARTY_ID" VARCHAR(20) PRIMARY KEY,
              "PARTY_TYPE_ID" VARCHAR(20), "LAST_UPDATED_STAMP" TIMESTAMP)
            """);
        monolith.execute("""
            CREATE TABLE "PERSON" ("PARTY_ID" VARCHAR(20) PRIMARY KEY,
              "FIRST_NAME" VARCHAR(60), "LAST_NAME" VARCHAR(60))
            """);
        monolith.execute("CREATE TABLE \"PARTY_GROUP\" (\"PARTY_ID\" VARCHAR(20) PRIMARY KEY, \"GROUP_NAME\" VARCHAR(100))");
        monolith.execute("""
            CREATE TABLE "PARTY_ROLE" ("PARTY_ID" VARCHAR(20),
              "ROLE_TYPE_ID" VARCHAR(20), PRIMARY KEY ("PARTY_ID","ROLE_TYPE_ID"))
            """);
        MonolithSyncWriter writer = new JdbcMonolithSyncWriter(monolith);
        service = new ReverseSyncService(writer);
    }

    @AfterAll
    static void stopMonolith() {
        MONOLITH.stop();
    }

    @Test
    void partyEventUpsertsMonolithRowsAndNewServiceWins() {
        // monolith has stale data for P1
        monolith.update("INSERT INTO \"PARTY\" VALUES ('P1','PERSON',null)");
        monolith.update("INSERT INTO \"PERSON\" VALUES ('P1','Old','Name')");
        monolith.update("INSERT INTO \"PARTY_ROLE\" VALUES ('P1','STALE_ROLE')");

        service.onPartyEvent(new ReverseSyncService.PartySnapshot(
            "P1", "PERSON", "Ada", "King", null, Set.of("CUSTOMER", "SUPPLIER")));

        assertEquals("Ada", monolith.queryForObject(
            "SELECT \"FIRST_NAME\" FROM \"PERSON\" WHERE \"PARTY_ID\"='P1'", String.class));
        assertEquals(Set.of("CUSTOMER", "SUPPLIER"), Set.copyOf(monolith.queryForList(
            "SELECT \"ROLE_TYPE_ID\" FROM \"PARTY_ROLE\" WHERE \"PARTY_ID\"='P1'", String.class)));

        // updated event revokes SUPPLIER — role disappears (new service wins)
        service.onPartyEvent(new ReverseSyncService.PartySnapshot(
            "P1", "PERSON", "Ada", "King", null, Set.of("CUSTOMER")));
        assertEquals(Set.of("CUSTOMER"), Set.copyOf(monolith.queryForList(
            "SELECT \"ROLE_TYPE_ID\" FROM \"PARTY_ROLE\" WHERE \"PARTY_ID\"='P1'", String.class)));

        // group party flows into PARTY_GROUP
        service.onPartyEvent(new ReverseSyncService.PartySnapshot(
            "G1", "PARTY_GROUP", null, null, "Acme Corp", Set.of()));
        assertEquals("Acme Corp", monolith.queryForObject(
            "SELECT \"GROUP_NAME\" FROM \"PARTY_GROUP\" WHERE \"PARTY_ID\"='G1'", String.class));
    }
}
