package notification.application;

import notification.domain.RenderedEmail;

/** Outbound mail port; the SMTP adapter implements it. */
public interface MailSender {

    void send(String recipient, RenderedEmail email);
}
