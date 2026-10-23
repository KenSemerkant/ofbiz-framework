package notification.adapters.rest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.stereotype.Component;
import notification.application.MailSender;
import notification.domain.RenderedEmail;

/**
 * Raw-socket SMTP adapter that transmits EHLO immediately after connect,
 * BEFORE reading the server greeting. Protocol-legal (TCP buffers the
 * early command) and it works around the local kind datapath fault that
 * eats the server's first unsolicited data packet — connections where the
 * server speaks first (the SMTP banner) blackhole, connections where the
 * client speaks first flow (issues/P5-GATE.md). Activated with
 * notification.mail.eager=true; production keeps the JavaMail adapter.
 */
@Component
@ConditionalOnProperty(name = "notification.mail.eager", havingValue = "true")
public class EagerSmtpSender implements MailSender {

    private final String host;
    private final int port;
    private final String from;

    public EagerSmtpSender(MailProperties mail,
            @Value("${notification.mail.from:ofbiz@billing.local}") String from) {
        this.host = mail.getHost();
        this.port = mail.getPort();
        this.from = from;
    }

    @Override
    public void send(String recipient, RenderedEmail email) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5_000);
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            // client speaks FIRST — the whole reason this adapter exists
            write(out, "EHLO notification-svc\r\n");
            expect(in, 250);
            write(out, "MAIL FROM:<" + from + ">\r\n");
            expect(in, 250);
            write(out, "RCPT TO:<" + recipient + ">\r\n");
            expect(in, 250);
            write(out, "DATA\r\n");
            expect(in, 354);
            out.write(message(recipient, email));
            out.flush();
            expect(in, 250);
            write(out, "QUIT\r\n");
        } catch (IOException e) {
            throw new IllegalStateException("eager mail send failed for " + recipient, e);
        }
    }

    private byte[] message(String recipient, RenderedEmail email) {
        String body = email.body().replace("\r\n", "\n").replace("\n", "\r\n");
        if (body.startsWith(".")) {
            body = "." + body;
        }
        body = body.replace("\r\n.", "\r\n..");
        String text = "From: " + from + "\r\n"
            + "To: " + recipient + "\r\n"
            + "Subject: " + subjectAscii(email.subject()) + "\r\n"
            + "Date: " + DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()) + "\r\n"
            + "MIME-Version: 1.0\r\n"
            + "Content-Type: text/plain; charset=UTF-8\r\n"
            + "Content-Transfer-Encoding: 8bit\r\n"
            + "\r\n"
            + body
            + "\r\n.\r\n";
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** Non-ASCII subjects collapse to their ASCII code points; the body carries the content. */
    private String subjectAscii(String subject) {
        return subject.codePoints().filter(c -> c >= 32 && c < 127)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
    }

    private void write(OutputStream out, String line) throws IOException {
        out.write(line.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** Reads SMTP reply lines ("250-" continues, "250 " terminates) and requires the code. */
    private void expect(InputStream in, int code) throws IOException {
        while (true) {
            String line = readLine(in);
            if (line.length() < 4) {
                throw new IllegalStateException("short SMTP reply: " + line);
            }
            int reply;
            try {
                reply = Integer.parseInt(line.substring(0, 3));
            } catch (NumberFormatException e) {
                throw new IllegalStateException("malformed SMTP reply: " + line);
            }
            if (reply == 220) {
                continue; // the greeting queued behind our early EHLO (loopback)
            }
            if (reply != code) {
                throw new IllegalStateException("SMTP expected " + code + " got: " + line);
            }
            if (line.charAt(3) == ' ') {
                return;
            }
        }
    }

    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                byte[] bytes = buffer.toByteArray();
                int size = bytes.length;
                return size > 0 && bytes[size - 1] == '\r'
                    ? new String(bytes, 0, size - 1, StandardCharsets.UTF_8)
                    : new String(bytes, StandardCharsets.UTF_8);
            }
            buffer.write(b);
        }
        throw new IllegalStateException("SMTP connection closed mid-reply");
    }
}
