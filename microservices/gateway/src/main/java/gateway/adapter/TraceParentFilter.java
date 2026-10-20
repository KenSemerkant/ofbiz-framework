package gateway.adapter;

import java.security.SecureRandom;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

// Ensure upstream calls carry a W3C traceparent (generate when the caller
// did not send one) so monolith spans chain onto gateway traces.
@Component
public class TraceParentFilter implements GlobalFilter, Ordered {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (exchange.getRequest().getHeaders().containsHeader("traceparent")) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request =
            exchange.getRequest().mutate().header("traceparent", generate()).build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    private static String generate() {
        return "00-" + hex(16) + "-" + hex(8) + "-01";
    }

    private static String hex(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (byte b : value) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
