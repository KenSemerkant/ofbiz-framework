package gateway.adapter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                // CORS preflight carries no credentials
                .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // kubelet probes carry no credentials (CG-1 on-cluster)
                .pathMatchers("/actuator/health/**").permitAll()
                // OIDC endpoints proxied to identity-svc: the browser runs
                // the auth-code+PKCE dance here BEFORE holding a token
                // (route `identity-svc` in application.yaml)
                .pathMatchers("/oauth2/**", "/login/**", "/logout/**", "/.well-known/**").permitAll()
                .anyExchange().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
