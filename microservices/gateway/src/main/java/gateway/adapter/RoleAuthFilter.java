package gateway.adapter;

import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * P2-F3-S1: route-level RBAC. A route declaring metadata `required-role`
 * only admits tokens whose `roles` claim (issued by identity-svc from the
 * party projection) contains it; otherwise 403.
 */
@Component
public class RoleAuthFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = (Route) exchange.getAttributes()
            .get(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null || route.getMetadata().get("required-role") == null) {
            return chain.filter(exchange);
        }
        String required = route.getMetadata().get("required-role").toString();
        return ReactiveSecurityContextHolder.getContext()
            .map(ctx -> ctx.getAuthentication() != null
                && ctx.getAuthentication().getPrincipal() instanceof Jwt jwt
                && rolesOf(jwt).contains(required))
            .defaultIfEmpty(false)
            .flatMap(allowed -> Boolean.TRUE.equals(allowed)
                ? chain.filter(exchange)
                : Mono.defer(() -> {
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return exchange.getResponse().setComplete();
                }));
    }

    @Override
    public int getOrder() {
        return 10; // after the resource-server security filters authenticate
    }

    static List<String> rolesOf(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles == null ? List.of() : roles;
    }
}
