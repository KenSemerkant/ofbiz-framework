package gateway.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Strangler bridge for the monolith route (P1-F2-S3): forward the validated
 * JWT subject as X-Auth-User and pin the upstream Host header.
 */
@Component
public class MonolithBridgeFilter implements GlobalFilter, Ordered {

    private final String hostHeader;

    public MonolithBridgeFilter(
        @Value("${gateway.monolith.host-header:ofbiz-monolith.ofbiz.svc}") String hostHeader) {
        this.hostHeader = hostHeader;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) {
            route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_PREDICATE_ROUTE_ATTR);
        }
        if (route == null || !"monolith".equals(route.getId())) {
            return chain.filter(exchange);
        }
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .flatMap(auth -> Mono.justOrEmpty(
                auth != null && auth.getPrincipal() instanceof Jwt jwt ? jwt : null))
            .map(jwt -> bridge(exchange, jwt.getSubject()))
            .defaultIfEmpty(exchange)
            .flatMap(chain::filter);
    }

    private ServerWebExchange bridge(ServerWebExchange exchange, String subject) {
        ServerHttpRequest request = exchange.getRequest().mutate()
            .header("X-Auth-User", subject != null ? subject : "")
            .header(HttpHeaders.HOST, hostHeader)
            .build();
        // Make Netty send the pinned Host instead of the routed URI's.
        exchange.getAttributes().put(ServerWebExchangeUtils.PRESERVE_HOST_HEADER_ATTRIBUTE, true);
        return exchange.mutate().request(request).build();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1; // before NettyRoutingFilter
    }
}
