package notification.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * P1-F4-S1 AC a + idempotency: an event on notification.requested.v1 becomes
 * one mail (GreenMail stands in for staging Mailhog), and a redelivered
 * notificationId produces no second mail.
 */
@SpringBootTest
class NotificationDeliveryTest {

    private static final String EVENT = """
            {"notificationId":"ntf-delivery-1","channel":"EMAIL",
             "recipient":"kadir@example.com","template":"welcome","locale":"de",
             "params":{"name":"Kadir"}}
            """;

    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.0"));
    private static final org.testcontainers.containers.PostgreSQLContainer<?> POSTGRES =
            new org.testcontainers.containers.PostgreSQLContainer<>("postgres:17");
    private static final GreenMail GREEN_MAIL = new GreenMail(ServerSetupTest.SMTP);

    static {
        KAFKA.start();
        POSTGRES.start();
        GREEN_MAIL.start();
    }

    @DynamicPropertySource
    static void serviceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> GREEN_MAIL.getSmtp().getPort());
    }

    private final KafkaTemplate<Object, Object> kafka;

    NotificationDeliveryTest(@Autowired ProducerFactory<Object, Object> producerFactory) {
        this.kafka = new KafkaTemplate<>(producerFactory);
    }

    @AfterAll
    static void stopGreenMail() {
        GREEN_MAIL.stop();
    }

    @Test
    void eventBecomesOneLocalizedMailAndRedeliveryIsSkipped() throws Exception {
        kafka.send("notification.requested.v1", EVENT);

        assertTrue(GREEN_MAIL.waitForIncomingEmail(15_000, 1), "mail did not arrive");
        MimeMessage message = GREEN_MAIL.getReceivedMessages()[0];
        assertEquals("kadir@example.com", message.getRecipients(MimeMessage.RecipientType.TO)[0].toString());
        assertEquals("Willkommen bei OFBiz, Kadir!", message.getSubject());
        assertTrue(String.valueOf(message.getContent()).contains("Hallo Kadir,"), "German body with params");

        // Redelivery of the same notificationId must not produce a second mail.
        kafka.send("notification.requested.v1", EVENT);
        Thread.sleep(3_000);
        assertEquals(1, GREEN_MAIL.getReceivedMessages().length, "redelivery must be deduped");
    }
}
