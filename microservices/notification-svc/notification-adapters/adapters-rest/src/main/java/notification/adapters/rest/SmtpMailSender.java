package notification.adapters.rest;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import notification.application.MailSender;
import notification.domain.RenderedEmail;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP adapter for the MailSender port; configured by spring.mail.*.
 * Default adapter — the staging-only eager variant (notification.mail.eager)
 * replaces it where the local kind datapath needs a client-first client.
 */
@Component
@ConditionalOnProperty(name = "notification.mail.eager", havingValue = "false",
        matchIfMissing = true)
public class SmtpMailSender implements MailSender {

    private final JavaMailSender mailSender;

    SmtpMailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(String recipient, RenderedEmail email) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            message.setRecipient(MimeMessage.RecipientType.TO, new InternetAddress(recipient));
            message.setSubject(email.subject(), "UTF-8");
            message.setText(email.body(), "UTF-8");
            mailSender.send(message);
        } catch (Exception e) {
            throw new IllegalStateException("mail send failed for " + recipient, e);
        }
    }
}
