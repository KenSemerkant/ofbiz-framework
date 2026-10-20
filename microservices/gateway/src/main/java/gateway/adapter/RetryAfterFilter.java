package gateway.adapter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

// SCG's rate limiter returns 429 without Retry-After; add one (seconds).
@Component
public class RetryAfterFilter implements WebFilter {

    static final String RETRY_AFTER_SECONDS = "1";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            var status = exchange.getResponse().getStatusCode();
            if (status != null && status.value() == HttpStatus.TOO_MANY_REQUESTS.value()
                && !exchange.getResponse().getHeaders().containsHeader(HttpHeaders.RETRY_AFTER)) {
                exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
            }
            return Mono.empty();
        });
        return chain.filter(exchange);
    }
}
