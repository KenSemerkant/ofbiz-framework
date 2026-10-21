package search.adapters.rest;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import search.adapters.grpc.SearchGrpcService;
import search.application.SearchService;

/** East-west gRPC endpoint (ADR-010). Netty-shaded server, own port. */
@Configuration
public class GrpcServerConfig {

    @Bean
    SearchGrpcService searchGrpcService(SearchService searchService) {
        return new SearchGrpcService(searchService);
    }

    @Bean(destroyMethod = "shutdownNow")
    Server searchGrpcServer(SearchGrpcService service,
            @Value("${search.grpc.port}") int port) throws IOException {
        return ServerBuilder.forPort(port)
            .addService(service)
            .build()
            .start();
    }
}
