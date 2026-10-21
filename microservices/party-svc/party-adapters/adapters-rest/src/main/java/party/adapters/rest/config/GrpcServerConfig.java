package party.adapters.rest.config;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import party.adapters.grpc.PartyGrpcService;
import party.application.PartyService;

/** East-west gRPC endpoint (ADR-010). Netty-shaded server, own port. */
@Configuration
public class GrpcServerConfig {

    @Bean
    PartyGrpcService partyGrpcService(PartyService partyService) {
        return new PartyGrpcService(partyService);
    }

    @Bean(destroyMethod = "shutdownNow")
    Server partyGrpcServer(PartyGrpcService service,
            @Value("${party.grpc.port}") int port) throws IOException {
        return ServerBuilder.forPort(port)
            .addService(service)
            .build()
            .start();
    }
}
