package product.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import product.application.ReverseSyncService;
import product.adapters.rest.persistence.JdbcMonolithProductSyncWriter;
import product.domain.ProductAssoc;
import product.domain.ProductPrice;

/**
 * P3-GATE AC: product events apply back to the monolith product slice;
 * conflict policy new-service-wins proven (stale row overwritten, dropped
 * membership/price disappears, delete removes the whole slice).
 */
class ProductReverseSyncServiceTest {

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
            CREATE TABLE "PRODUCT" ("PRODUCT_ID" VARCHAR(20) PRIMARY KEY,
              "PRODUCT_TYPE_ID" VARCHAR(20), "INTERNAL_NAME" VARCHAR(255),
              "PRODUCT_NAME" VARCHAR(255), "LAST_UPDATED_STAMP" TIMESTAMP)
            """);
        monolith.execute("""
            CREATE TABLE "PRODUCT_CATEGORY" ("PRODUCT_CATEGORY_ID" VARCHAR(20) PRIMARY KEY,
              "CATEGORY_NAME" VARCHAR(100))
            """);
        monolith.execute("""
            CREATE TABLE "PRODUCT_CATEGORY_MEMBER" ("PRODUCT_ID" VARCHAR(20),
              "PRODUCT_CATEGORY_ID" VARCHAR(20), "FROM_DATE" TEXT,
              PRIMARY KEY ("PRODUCT_ID","PRODUCT_CATEGORY_ID"))
            """);
        monolith.execute("""
            CREATE TABLE "PRODUCT_PRICE" ("PRODUCT_ID" VARCHAR(20),
              "PRODUCT_PRICE_TYPE_ID" VARCHAR(20), "CURRENCY_UOM_ID" VARCHAR(20),
              "FROM_DATE" TEXT, "PRICE" TEXT,
              PRIMARY KEY ("PRODUCT_ID","PRODUCT_PRICE_TYPE_ID","CURRENCY_UOM_ID","FROM_DATE"))
            """);
        monolith.execute("""
            CREATE TABLE "PRODUCT_ASSOC" ("PRODUCT_ID" VARCHAR(20), "PRODUCT_ID_TO" VARCHAR(20),
              "PRODUCT_ASSOC_TYPE_ID" VARCHAR(20), "FROM_DATE" TEXT,
              PRIMARY KEY ("PRODUCT_ID","PRODUCT_ID_TO","PRODUCT_ASSOC_TYPE_ID","FROM_DATE"))
            """);
        service = new ReverseSyncService(new JdbcMonolithProductSyncWriter(monolith));
    }

    @AfterAll
    static void stopMonolith() {
        MONOLITH.stop();
    }

    @Test
    void productEventUpsertsMonolithRowsAndNewServiceWins() {
        // monolith has stale data for PRD1
        monolith.update(
            "INSERT INTO \"PRODUCT\" VALUES ('PRD1','FINISHED_GOOD','Old','Old Name',null)");
        monolith.update(
            "INSERT INTO \"PRODUCT_CATEGORY_MEMBER\" VALUES ('PRD1','STALE_CAT','0')");
        monolith.update("INSERT INTO \"PRODUCT_PRICE\" VALUES "
            + "('PRD1','STALE_TYPE','EUR','0','5.00')");

        service.onProductEvent(new ReverseSyncService.ProductSnapshot(
            "PRD1", "FINISHED_GOOD", "Widget", "Blue Widget",
            Set.of("CAT1"),
            List.of(new ProductPrice("DEFAULT_PRICE", "USD",
                Instant.ofEpochMilli(0), null, new BigDecimal("19.99"))),
            List.of(new ProductAssoc("PRD2", "PRODUCT_VARIANT",
                Instant.ofEpochMilli(0), null))));

        assertEquals("Blue Widget", monolith.queryForObject(
            "SELECT \"PRODUCT_NAME\" FROM \"PRODUCT\" WHERE \"PRODUCT_ID\"='PRD1'",
            String.class));
        assertEquals(Set.of("CAT1"), Set.copyOf(monolith.queryForList(
            "SELECT \"PRODUCT_CATEGORY_ID\" FROM \"PRODUCT_CATEGORY_MEMBER\" "
            + "WHERE \"PRODUCT_ID\"='PRD1'", String.class)));
        assertEquals("19.99", monolith.queryForObject(
            "SELECT \"PRICE\" FROM \"PRODUCT_PRICE\" "
            + "WHERE \"PRODUCT_ID\"='PRD1' AND \"PRODUCT_PRICE_TYPE_ID\"='DEFAULT_PRICE'",
            String.class));
        assertEquals(1, monolith.queryForObject(
            "SELECT count(*) FROM \"PRODUCT_ASSOC\" WHERE \"PRODUCT_ID\"='PRD1'",
            Integer.class));

        // updated event drops the price — row disappears (new service wins)
        service.onProductEvent(new ReverseSyncService.ProductSnapshot(
            "PRD1", "FINISHED_GOOD", "Widget", "Blue Widget", Set.of("CAT1"),
            List.of(), List.of()));
        assertEquals(0, monolith.queryForObject(
            "SELECT count(*) FROM \"PRODUCT_PRICE\" WHERE \"PRODUCT_ID\"='PRD1'",
            Integer.class));

        // category rename flows into PRODUCT_CATEGORY
        service.onCategoryEvent("CAT1", "Renamed Widgets");
        assertEquals("Renamed Widgets", monolith.queryForObject(
            "SELECT \"CATEGORY_NAME\" FROM \"PRODUCT_CATEGORY\" "
            + "WHERE \"PRODUCT_CATEGORY_ID\"='CAT1'", String.class));

        // delete removes the whole slice
        service.onProductDeleted("PRD1");
        assertEquals(0, monolith.queryForObject(
            "SELECT count(*) FROM \"PRODUCT\" WHERE \"PRODUCT_ID\"='PRD1'", Integer.class));
        assertEquals(0, monolith.queryForObject(
            "SELECT count(*) FROM \"PRODUCT_CATEGORY_MEMBER\" WHERE \"PRODUCT_ID\"='PRD1'",
            Integer.class));
        assertEquals(0, monolith.queryForObject(
            "SELECT count(*) FROM \"PRODUCT_ASSOC\" WHERE \"PRODUCT_ID\"='PRD1'",
            Integer.class));
    }
}
