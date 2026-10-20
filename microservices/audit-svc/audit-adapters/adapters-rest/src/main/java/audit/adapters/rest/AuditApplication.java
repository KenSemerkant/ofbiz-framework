package audit.adapters.rest;

import audit.application.AlertPublisher;
import audit.application.AuditEventStore;
import audit.application.RecordAuditEventUseCase;
import audit.domain.AlertPolicy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AuditApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditApplication.class, args);
    }

    @Bean
    RecordAuditEventUseCase recordAuditEventUseCase(AuditEventStore store, AlertPublisher alerts) {
        return new RecordAuditEventUseCase(store, alerts, new AlertPolicy());
    }
}
