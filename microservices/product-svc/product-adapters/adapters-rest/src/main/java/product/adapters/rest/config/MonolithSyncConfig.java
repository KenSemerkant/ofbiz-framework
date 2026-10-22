package product.adapters.rest.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import product.application.MonolithSyncWriter;
import product.application.ReverseSyncService;
import product.adapters.rest.persistence.JdbcMonolithProductSyncWriter;

/**
 * P3-GATE: second datasource for the monolith DB (reverse-sync write-back,
 * ADR-012). Disabled unless product.monolith.sync.enabled=true so tests
 * that do not exercise reverse-sync boot without a monolith.
 */
@Configuration
@ConditionalOnProperty(name = "product.monolith.sync.enabled", havingValue = "true")
public class MonolithSyncConfig {

    /**
     * The product-svc own database (PRODUCT_DB_* env → spring.datasource.*).
     * Declaring any DataSource bean makes Boot's auto-configuration back
     * off, so the primary one must be explicit or Flyway/JPA would pick
     * the monolith datasource instead (bit party-svc in P2).
     */
    @Bean
    @org.springframework.context.annotation.Primary
    public DataSource productDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        return DataSourceBuilder.create()
            .url(url).username(username).password(password).build();
    }

    @Bean(name = "monolithDataSource")
    DataSource monolithDataSource(
            @Value("${product.monolith.datasource.url}") String url,
            @Value("${product.monolith.datasource.username}") String username,
            @Value("${product.monolith.datasource.password}") String password) {
        return DataSourceBuilder.create()
            .url(url).username(username).password(password).build();
    }

    @Bean(name = "monolithJdbc")
    public JdbcTemplate monolithJdbc(
            @Qualifier("monolithDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public MonolithSyncWriter monolithSyncWriter(
            @Qualifier("monolithJdbc") JdbcTemplate monolith) {
        return new JdbcMonolithProductSyncWriter(monolith);
    }

    @Bean
    public ReverseSyncService reverseSyncService(MonolithSyncWriter writer) {
        return new ReverseSyncService(writer);
    }
}
