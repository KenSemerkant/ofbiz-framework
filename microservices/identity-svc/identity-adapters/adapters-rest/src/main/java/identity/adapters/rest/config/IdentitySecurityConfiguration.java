package identity.adapters.rest.config;

import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * Identity edge security (P1-F1-S2).
 *
 * <p>Three chains: the authorization-server endpoints (as Boot's
 * default-web-security auto-config would build them), a stateless
 * HTTP-Basic admin API restricted to ROLE_ADMIN, and a form-login chain
 * for the rest (login page, logout).
 *
 * <p>Defining any chain disables Boot's {@code @ConditionalOnDefaultWebSecurity}
 * configurations, so the first chain below intentionally mirrors them.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class IdentitySecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        http.oauth2AuthorizationServer(authorizationServer -> {
            http.securityMatcher(authorizationServer.getEndpointsMatcher());
            authorizationServer.oidc(Customizer.withDefaults());
        });
        http
            .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .exceptionHandling(handling -> handling.defaultAuthenticationEntryPointFor(
                new LoginUrlAuthenticationEntryPoint("/login"),
                htmlRequests()));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain adminApiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/v1/users", "/api/v1/users/**")
            .authorizeHttpRequests(requests -> requests.anyRequest().hasRole("ADMIN"))
            .httpBasic(Customizer.withDefaults())
            .exceptionHandling(handling -> {
                var entryPoint = new org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint();
                entryPoint.setRealmName("identity-admin");
                handling.authenticationEntryPoint(entryPoint);
            })
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(requests -> requests
                .requestMatchers("/error", "/actuator/health/**", "/actuator/prometheus").permitAll()
                .anyRequest().authenticated())
            .formLogin(Customizer.withDefaults());
        return http.build();
    }

    private static MediaTypeRequestMatcher htmlRequests() {
        MediaTypeRequestMatcher matcher = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        matcher.setIgnoredMediaTypes(Set.of(MediaType.ALL));
        return matcher;
    }
}
