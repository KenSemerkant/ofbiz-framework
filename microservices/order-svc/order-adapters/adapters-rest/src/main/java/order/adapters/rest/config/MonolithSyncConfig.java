package order.adapters.rest.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * P4-GATE: second datasource for the monolith DB (reverse-sync write-back,
 * ADR-012; product-svc template). Disabled unless order.monolith.sync.enabled
 * so tests without a monolith boot fine. The @Primary own-DB beans follow
 * the party-svc P2 lesson: declaring any DataSource/JdbcTemplate bean makes
 * Boot's auto-config back off.
 */
@Configuration
@ConditionalOnProperty(name = "order.monolith.sync.enabled", havingValue = "true")
public class MonolithSyncConfig {

    @Bean
    @Primary
    public DataSource orderDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        return DataSourceBuilder.create()
            .url(url).username(username).password(password).build();
    }

    @Bean(name = "monolithDataSource")
    DataSource monolithDataSource(
            @Value("${order.monolith.datasource.url}") String url,
            @Value("${order.monolith.datasource.username}") String username,
            @Value("${order.monolith.datasource.password}") String password) {
        return DataSourceBuilder.create()
            .url(url).username(username).password(password).build();
    }

    @Bean(name = "monolithJdbc")
    public JdbcTemplate monolithJdbc(
            @Qualifier("monolithDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @Primary
    public JdbcTemplate orderJdbc(
            @Qualifier("orderDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
