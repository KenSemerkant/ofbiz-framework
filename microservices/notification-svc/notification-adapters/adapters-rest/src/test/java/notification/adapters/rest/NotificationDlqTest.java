package notification.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * P1-F4-S1 AC b: SMTP pointed at a closed port — the consumer retries with
 * backoff and the message lands on notification.requested.v1.dlq.
 */
@SpringBootTest
class NotificationDlqTest {

    private static final String EVENT = """
            {"notificationId":"ntf-dlq-1","channel":"EMAIL",
             "recipient":"kadir@example.com","template":"welcome","locale":"en",
             "params":{"name":"Kadir"}}
            """;

    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.0"));
    private static final org.testcontainers.containers.PostgreSQLContainer<?> POSTGRES =
            new org.testcontainers.containers.PostgreSQLContainer<>("postgres:17");

    /** A port nothing listens on — every SMTP attempt fails. */
    private static final int CLOSED_SMTP_PORT = findClosedPort();

    static {
        KAFKA.start();
        POSTGRES.start();
    }

    private static int findClosedPort() {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void serviceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> CLOSED_SMTP_PORT);
    }

    @TestConfiguration
    static class DlqProbe {
        static final CountDownLatch DLQ_RECEIVED = new CountDownLatch(1);

        @Bean
        DlqListener dlqListener() {
            return new DlqListener();
        }
    }

    static class DlqListener {
        @KafkaListener(topics = "notification.requested.v1.dlq", groupId = "dlq-probe")
        public void onDlq(String event) {
            DlqProbe.DLQ_RECEIVED.countDown();
        }
    }

    private final KafkaTemplate<Object, Object> kafka;

    NotificationDlqTest(@Autowired ProducerFactory<Object, Object> producerFactory) {
        this.kafka = new KafkaTemplate<>(producerFactory);
    }

    @Test
    void sendFailureRetriesThenDeadLetters() throws Exception {
        kafka.send("notification.requested.v1", EVENT);
        assertTrue(DlqProbe.DLQ_RECEIVED.await(60, TimeUnit.SECONDS),
                "message never reached notification.requested.v1.dlq");
    }
}
