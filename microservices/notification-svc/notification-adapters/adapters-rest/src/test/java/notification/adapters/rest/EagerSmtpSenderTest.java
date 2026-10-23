package notification.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import notification.domain.RenderedEmail;

/**
 * The eager adapter speaks before the greeting — this test server does NOT
 * send its banner until the client's first command arrives, mirroring the
 * datapath the adapter was built for (issues/P5-GATE.md).
 */
class EagerSmtpSenderTest {

    final ServerSocket server = new ServerSocket(0);
    final AtomicBoolean bannerSentEarly = new AtomicBoolean();
    final CopyOnWriteArrayList<String> transcript = new CopyOnWriteArrayList<>();
    final Thread listener;

    EagerSmtpSenderTest() throws Exception {
        server.setSoTimeout(10_000);
        listener = new Thread(this::serve);
        listener.setDaemon(true);
        listener.start();
    }

    private void serve() {
        try (Socket socket = server.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            // hold the greeting until the client has spoken (client-first)
            String first = in.readLine();
            bannerSentEarly.set(first != null && first.startsWith("EHLO"));
            transcript.add(first);
            out.print("220 test ESMTP\r\n");
            out.print("250-greets\r\n250 SMTPUTF8\r\n");
            out.flush();
            String line;
            while ((line = in.readLine()) != null) {
                transcript.add(line);
                if (line.startsWith("MAIL FROM") || line.startsWith("RCPT TO")) {
                    out.print("250 ok\r\n");
                } else if (line.equals("DATA")) {
                    out.print("354 go\r\n");
                } else if (line.equals(".")) {
                    out.print("250 queued\r\n");
                } else if (line.equals("QUIT")) {
                    out.print("221 bye\r\n");
                    out.flush();
                    return;
                }
                out.flush();
            }
        } catch (Exception e) {
            // socket closed mid-test — transcript holds what we need
        }
    }

    @AfterEach
    void stop() throws Exception {
        server.close();
        listener.join(2_000);
    }

    private MailProperties properties() {
        MailProperties properties = new MailProperties();
        properties.setHost("localhost");
        properties.setPort(server.getLocalPort());
        return properties;
    }

    @Test
    void sendsEhloBeforeReadingBannerAndDeliversMessage() {
        EagerSmtpSender sender = new EagerSmtpSender(properties(), "ofbiz@test.local");
        sender.send("cust@test.local", new RenderedEmail("Invoice INV-1 issued",
            "Your invoice for 22.00 USD"));
        assertTrue(bannerSentEarly.get(), "EHLO must arrive before the server greeting");
        assertEquals(java.util.List.of(
            "EHLO notification-svc",
            "MAIL FROM:<ofbiz@test.local>",
            "RCPT TO:<cust@test.local>",
            "DATA",
            "QUIT"), transcript.stream().filter(l -> l.startsWith("EHLO")
                || l.startsWith("MAIL FROM") || l.startsWith("RCPT TO")
                || l.equals("DATA") || l.equals("QUIT")).toList());
        assertTrue(transcript.stream().anyMatch(l -> l.contains("Subject: Invoice INV-1 issued")));
        assertTrue(transcript.stream().anyMatch(l -> l.contains("22.00 USD")));
    }

    @Test
    void rejectsErrorReplies() throws Exception {
        try (ServerSocket rejectServer = new ServerSocket(0)) {
            rejectServer.setSoTimeout(10_000);
            Thread t = new Thread(() -> {
                try (Socket s = rejectServer.accept();
                        BufferedReader in = new BufferedReader(new InputStreamReader(
                            s.getInputStream(), StandardCharsets.UTF_8))) {
                    java.io.OutputStream raw = s.getOutputStream();
                    raw.write("250 ok\r\n".getBytes(StandardCharsets.UTF_8)); // EHLO
                    in.readLine();
                    raw.write("250 ok\r\n".getBytes(StandardCharsets.UTF_8)); // MAIL
                    in.readLine();
                    raw.flush();
                    raw.write("550 no such user\r\n".getBytes(StandardCharsets.UTF_8));
                    raw.flush();                                          // RCPT
                    in.readLine();
                } catch (Exception e) {
                    // closed
                }
            });
            t.setDaemon(true);
            t.start();
            MailProperties rejectProps = new MailProperties();
            rejectProps.setHost("localhost");
            rejectProps.setPort(rejectServer.getLocalPort());
            EagerSmtpSender sender = new EagerSmtpSender(rejectProps, "x@t.local");
            IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> sender.send("nobody@t.local", new RenderedEmail("s", "b")));
            String detail = String.valueOf(error.getCause() != null
                ? error.getCause().getMessage() : error.getMessage());
            assertTrue(detail.contains("550"), "cause should carry the 550: " + detail);
        }
    }


    @Test
    void malformedAndShortRepliesRejected() throws Exception {
        try (ServerSocket bad = new ServerSocket(0)) {
            bad.setSoTimeout(10_000);
            Thread t = new Thread(() -> {
                try (Socket s = bad.accept();
                        BufferedReader in = new BufferedReader(new InputStreamReader(
                            s.getInputStream(), StandardCharsets.UTF_8));
                        java.io.OutputStream raw = s.getOutputStream()) {
                    raw.write("nope\r\n".getBytes(StandardCharsets.UTF_8)); // EHLO -> malformed
                    raw.flush();
                    in.readLine();
                } catch (Exception e) {
                    // closed
                }
            });
            t.setDaemon(true);
            t.start();
            MailProperties badProps = new MailProperties();
            badProps.setHost("localhost");
            badProps.setPort(bad.getLocalPort());
            EagerSmtpSender sender = new EagerSmtpSender(badProps, "x@t.local");
            IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> sender.send("nobody@t.local", new RenderedEmail("s", "b")));
            String detail = String.valueOf(error.getCause() != null
                ? error.getCause().getMessage() : error.getMessage());
            assertTrue(detail.contains("malformed") || detail.contains("short")
                || detail.contains("SMTP"), "should name the bad reply: " + detail);
        }
    }

    @Test
    void dotLeadingBodyIsStuffed() {
        // exercises the dot-stuffing branches through the transcript server
        EagerSmtpSender sender = new EagerSmtpSender(properties(), "ofbiz@test.local");
        sender.send("cust@test.local", new RenderedEmail("Sub ject", ".starts with dot\n.line two"));
        assertTrue(transcript.stream().anyMatch(l -> l.equals("..starts with dot")));
        assertTrue(transcript.stream().anyMatch(l -> l.equals("..line two")));
    }
}
