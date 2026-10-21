package party.adapters.rest.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.jdbc.DataSourceBuilder;
import party.application.MonolithSyncWriter;
import party.application.ReverseSyncService;
import party.adapters.rest.persistence.JdbcMonolithSyncWriter;

/**
 * P2-F2-S3: second datasource for the monolith DB (reverse-sync write-back,
 * ADR-012). Disabled unless party.monolith.sync.enabled=true so tests that
 * do not exercise reverse-sync boot without a monolith.
 */
@Configuration
@ConditionalOnProperty(name = "party.monolith.sync.enabled", havingValue = "true")
public class MonolithSyncConfig {

    /**
     * The party-svc own database (PARTY_DB_* env → spring.datasource.*).
     * Declaring any DataSource bean makes Boot's auto-configuration back
     * off, so the primary one must be explicit or Flyway/JPA would pick
     * the monolith datasource instead.
     */
    @Bean
    @org.springframework.context.annotation.Primary
    DataSource partyDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        return DataSourceBuilder.create()
            .url(url).username(username).password(password).build();
    }

    @Bean(name = "monolithDataSource")
    DataSource monolithDataSource(
            @Value("${party.monolith.datasource.url}") String url,
            @Value("${party.monolith.datasource.username}") String username,
            @Value("${party.monolith.datasource.password}") String password) {
        return DataSourceBuilder.create()
            .url(url).username(username).password(password).build();
    }

    @Bean(name = "monolithJdbc")
    JdbcTemplate monolithJdbc(
            @Qualifier("monolithDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    MonolithSyncWriter monolithSyncWriter(
            @Qualifier("monolithJdbc") JdbcTemplate monolith) {
        return new JdbcMonolithSyncWriter(monolith);
    }

    @Bean
    ReverseSyncService reverseSyncService(MonolithSyncWriter writer) {
        return new ReverseSyncService(writer);
    }
}
