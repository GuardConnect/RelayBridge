package ru.eyeone.relaybridge;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * SMTP transport independent of JavaMail. One instance is one session and one message.
 * AUTH is never sent without verified TLS. The trace receives stages and numeric replies only:
 * credentials, addresses and the message itself are never written to it.
 */
final class SmtpClient {
    private static final String CLIENT_NAME = "relaybridge.local";
    private static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    private static final int READ_TIMEOUT_MILLIS = 20_000;
    private static final int SESSION_LIMIT_SECONDS = 120;
    private static final int MAX_REPLY_LINES = 100;
    private static final int MAX_LINE_BYTES = 8192;
    private static final String STAGE_DATA_CONFIRMATION = "DATA / подтверждение приёма";
    private static final Pattern ENHANCED_CODE =
            Pattern.compile("(?<![0-9])([245]\\.[0-9]{1,3}\\.[0-9]{1,3})(?![0-9.])");

    /** Closes a session that exceeds the overall time limit, whatever it is waiting for. */
    private static final ScheduledExecutorService DEADLINES = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "smtp-deadline");
        thread.setDaemon(true);
        return thread;
    });

    private final Config config;
    private final Consumer<String> trace;
    private final SSLSocketFactory tlsFactory;

    private volatile Socket socket;
    private InputStream input;
    private OutputStream output;
    private String stage = "TCP";
    private boolean secure;

    private SmtpClient(Config config, Consumer<String> trace, SSLSocketFactory tlsFactory) {
        this.config = config;
        this.trace = trace;
        this.tlsFactory = tlsFactory;
    }

    static void send(Config config, String kind, String body, String id, Consumer<String> trace) throws Exception {
        send(config, kind, body, id, trace, (SSLSocketFactory) SSLSocketFactory.getDefault());
    }

    /**
     * Deliver one message.
     *
     * @throws IllegalArgumentException when the settings are invalid (nothing is sent)
     * @throws Senders.SendError        for every transport or server failure, with a displayable text
     */
    static void send(Config config, String kind, String body, String id, Consumer<String> trace,
                     SSLSocketFactory tlsFactory) throws Exception {
        config.validateEmail();
        new SmtpClient(config, trace, tlsFactory).deliver(kind, body, id);
    }

    // ---- Session ---------------------------------------------------------------------------

    private void deliver(String kind, String body, String id) throws Senders.SendError {
        Socket plain = new Socket();
        socket = plain;
        ScheduledFuture<?> deadline = DEADLINES.schedule(this::abort, SESSION_LIMIT_SECONDS, TimeUnit.SECONDS);
        boolean accepted = false;
        try {
            trace.accept("Сервер: " + config.host + ":" + config.port + "; " + config.tls + "; AUTH " + config.auth);
            step("DNS / TCP connect");
            plain.connect(new InetSocketAddress(config.host, config.port), CONNECT_TIMEOUT_MILLIS);
            plain.setSoTimeout(READ_TIMEOUT_MILLIS);
            openStreams();
            if (config.tls.equals("SSL")) startTls();

            step("Приветствие сервера");
            expect(readReply(), 220);
            Reply capabilities = hello();
            if (config.tls.equals("STARTTLS")) {
                if (!capabilities.has("STARTTLS"))
                    throw new Senders.SendError(false, "EHLO: сервер не объявил STARTTLS. Проверьте режим TLS и порт.");
                step("STARTTLS");
                command("STARTTLS", "STARTTLS");
                expect(readReply(), 220);
                startTls();
                // Capabilities announced before TLS are untrusted and must be requested again.
                capabilities = hello();
            }
            authenticate(capabilities);
            envelope(capabilities);
            transmit(mime(config, kind, body, id));
            accepted = true;
            trace.accept("УСПЕХ: SMTP-сервер принял письмо; это не подтверждает доставку во Входящие");
            quit();
        } catch (Senders.SendError refused) {
            trace.accept("ОШИБКА: " + refused.safe);
            throw refused;
        } catch (Exception failure) {
            String reason = failure instanceof EOFException
                    ? "Сервер закрыл соединение до ответа" : DeliveryErrors.describe(failure);
            String message = stage + ": " + reason;
            if (stage.equals(STAGE_DATA_CONFIRMATION))
                message += " Результат доставки неизвестен; при повторе возможен дубликат.";
            trace.accept("ОШИБКА: " + message);
            throw new Senders.SendError(true, message);
        } finally {
            deadline.cancel(false);
            abort();
            trace.accept(accepted ? "Сессия завершена: принято сервером" : "Сессия завершена: не подтверждено");
        }
    }

    private void abort() {
        try {
            socket.close();
        } catch (Exception ignored) {
            // Nothing useful can be done with a failed close.
        }
    }

    private void step(String next) {
        stage = next;
        trace.accept("Этап: " + next);
    }

    private void openStreams() throws IOException {
        input = socket.getInputStream();
        output = socket.getOutputStream();
    }

    /** Wrap the connection in TLS 1.2+ and verify the certificate against the configured host name. */
    private void startTls() throws IOException {
        step("TLS handshake / проверка сертификата");
        SSLSocket tls = (SSLSocket) tlsFactory.createSocket(socket, config.host, config.port, true);
        socket = tls;
        tls.setSoTimeout(READ_TIMEOUT_MILLIS);
        SSLParameters parameters = tls.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        tls.setSSLParameters(parameters);
        List<String> protocols = new ArrayList<>();
        for (String protocol : tls.getSupportedProtocols()) {
            if (protocol.equals("TLSv1.2") || protocol.equals("TLSv1.3")) protocols.add(protocol);
        }
        tls.setEnabledProtocols(protocols.toArray(new String[0]));
        tls.startHandshake();
        secure = true;
        openStreams();
        SSLSession session = tls.getSession();
        trace.accept("TLS: " + session.getProtocol() + " / " + session.getCipherSuite() + "; сертификат подтверждён");
    }

    private Reply hello() throws IOException, Senders.SendError {
        step("EHLO");
        command("EHLO " + CLIENT_NAME, "EHLO " + CLIENT_NAME);
        Reply reply = readReply();
        // HELO has no extensions, so it is only usable for an anonymous relay over implicit TLS.
        boolean ehloUnknown = reply.code == 500 || reply.code == 502 || reply.code == 504;
        if (ehloUnknown && config.auth.equals("NONE") && !config.tls.equals("STARTTLS")) {
            command("HELO " + CLIENT_NAME, "HELO " + CLIENT_NAME);
            reply = readReply();
        }
        expect(reply, 250);
        trace.accept("Возможности: STARTTLS=" + reply.has("STARTTLS")
                + "; AUTH=" + String.join(",", reply.mechanisms())
                + "; SMTPUTF8=" + reply.has("SMTPUTF8"));
        return reply;
    }

    private void authenticate(Reply capabilities) throws IOException, Senders.SendError {
        if (config.auth.equals("NONE")) {
            trace.accept("AUTH: выключена пользователем (SMTP relay)");
            return;
        }
        if (!secure)
            throw new Senders.SendError(false, "AUTH: авторизация разрешена только через проверенное TLS-соединение");
        Set<String> offered = capabilities.mechanisms();
        String mechanism = config.auth;
        if (mechanism.equals("AUTO"))
            mechanism = offered.contains("LOGIN") ? "LOGIN" : offered.contains("PLAIN") ? "PLAIN" : "";
        if (mechanism.isEmpty() || !offered.contains(mechanism))
            throw new Senders.SendError(false, "AUTH: сервер не объявил выбранный способ. Доступно: "
                    + String.join(",", offered) + ". Выберите способ во вкладке Отправка.");

        step("AUTH " + mechanism);
        String password = passwordForAuth(config);
        Reply result;
        if (mechanism.equals("LOGIN")) {
            command("AUTH LOGIN", "AUTH LOGIN");
            expect(readReply(), 334);
            command(base64(config.user), "<логин скрыт>");
            expect(readReply(), 334);
            command(base64(password), "<пароль скрыт>");
            result = readReply();
        } else if (mechanism.equals("PLAIN")) {
            String credentials = base64("\0" + config.user + "\0" + password);
            command("AUTH PLAIN " + credentials, "AUTH PLAIN <данные скрыты>");
            result = readReply();
            if (result.code == 334) {
                // The server ignored the initial response and asks for the credentials separately.
                command(credentials, "<данные AUTH скрыты>");
                result = readReply();
            }
        } else {
            throw new Senders.SendError(false, "AUTH: неизвестный способ авторизации");
        }
        expect(result, 235);
        trace.accept("AUTH: сервер подтвердил авторизацию");
    }

    /**
     * Google shows app passwords as four groups separated by spaces; they are sent without the separators.
     * Any other password, and any password of another server, is used exactly as entered.
     */
    static String passwordForAuth(Config config) {
        if (!SmtpDiagnostics.isGmail(config.host)) return config.password;
        String compact = config.password.replaceAll("[\\s\\p{Zs}\\u200B\\uFEFF]+", "");
        return compact.matches("[A-Za-z0-9]{16}") ? compact : config.password;
    }

    private void envelope(Reply capabilities) throws IOException, Senders.SendError {
        boolean international = !StandardCharsets.US_ASCII.newEncoder().canEncode(config.from + config.to);
        if (international && !capabilities.has("SMTPUTF8"))
            throw new Senders.SendError(false, "MAIL FROM: сервер не поддерживает международные адреса SMTPUTF8");
        step("MAIL FROM");
        command("MAIL FROM:<" + config.from + ">" + (international ? " SMTPUTF8" : ""), "MAIL FROM: <адрес скрыт>");
        expect(readReply(), 250);
        for (String recipient : config.recipients()) {
            step("RCPT TO");
            command("RCPT TO:<" + recipient + ">", "RCPT TO: <адрес скрыт>");
            expect(readReply(), 250, 251, 252);
        }
    }

    private void transmit(String message) throws IOException, Senders.SendError {
        step("DATA / запрос передачи письма");
        command("DATA", "DATA");
        expect(readReply(), 354);
        step("DATA / передача MIME");
        // Headers and the base64 body never begin a line with a dot, so no dot-stuffing is required.
        output.write(message.getBytes(StandardCharsets.UTF_8));
        output.write(".\r\n".getBytes(StandardCharsets.US_ASCII));
        output.flush();
        trace.accept("C: <заголовки и содержимое письма скрыты>");
        step(STAGE_DATA_CONFIRMATION);
        expect(readReply(), 250);
    }

    /** The message is already accepted; a failed goodbye must not turn success into a retry. */
    private void quit() {
        step("QUIT");
        try {
            command("QUIT", "QUIT");
            expect(readReply(), 221);
        } catch (Exception ignored) {
            trace.accept("QUIT: соединение закрыто после подтверждённого приёма; письмо не повторяется");
        }
    }

    // ---- Wire protocol ---------------------------------------------------------------------

    /** Send one command line; {@code visible} is its form for the trace, without secrets. */
    private void command(String wire, String visible) throws IOException {
        if (wire.indexOf('\r') >= 0 || wire.indexOf('\n') >= 0) throw new IOException("Invalid command");
        trace.accept("C: " + visible);
        output.write((wire + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    /** Read one, possibly multi-line, reply. */
    private Reply readReply() throws IOException {
        List<String> lines = new ArrayList<>();
        int code = -1;
        for (int count = 0; count < MAX_REPLY_LINES; count++) {
            String line = readLine();
            if (line.length() < 3 || !line.substring(0, 3).matches("[0-9]{3}"))
                throw new IOException("Malformed SMTP reply");
            int lineCode = Integer.parseInt(line.substring(0, 3));
            if (code != -1 && code != lineCode) throw new IOException("Inconsistent SMTP reply");
            code = lineCode;
            lines.add(line);
            trace.accept("S: " + code + enhanced(line) + (code == 334 ? " <вызов AUTH скрыт>" : ""));
            if (line.length() == 3 || line.charAt(3) == ' ') return new Reply(code, lines);
            if (line.charAt(3) != '-') throw new IOException("Malformed SMTP continuation");
        }
        throw new IOException("SMTP reply too long");
    }

    /** One CRLF-terminated line without the terminator. */
    private String readLine() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int previous = -1;
        for (int count = 0; count < MAX_LINE_BYTES; count++) {
            int next = input.read();
            if (next < 0) throw new EOFException("SMTP closed");
            if (previous == '\r' && next == '\n') {
                // The carriage return is already in the buffer; leave it out.
                return new String(buffer.toByteArray(), 0, buffer.size() - 1, StandardCharsets.US_ASCII);
            }
            buffer.write(next);
            previous = next;
        }
        throw new IOException("SMTP line too long");
    }

    /** Accept the listed reply codes; anything else ends the session with a numeric explanation. */
    private void expect(Reply reply, int... allowed) throws Senders.SendError {
        for (int code : allowed) {
            if (code == reply.code) return;
        }
        String text = String.join(" ", reply.lines);
        String numeric = reply.code + enhanced(text);
        String hint = stage.startsWith("AUTH")
                ? SmtpDiagnostics.auth(numeric, config.host, text)
                : "SMTP " + numeric + ": команда отклонена сервером";
        // 4xx is a temporary refusal and may be retried later; 5xx is final.
        throw new Senders.SendError(reply.code / 100 == 4, stage + ": " + hint);
    }

    private static String enhanced(String line) {
        Matcher matcher = ENHANCED_CODE.matcher(line);
        return matcher.find() ? " / " + matcher.group(1) : "";
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    // ---- Message ---------------------------------------------------------------------------

    /** A complete text/plain message: UTF-8 body in base64, subject as RFC 2047 encoded words. */
    static String mime(Config config, String kind, String body, String id) {
        String messageId = id.replaceAll("[^A-Za-z0-9._-]", "_");
        if (messageId.length() > 150) messageId = messageId.substring(0, 150);
        String date = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).format(new Date());
        String encodedBody = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(body.getBytes(StandardCharsets.UTF_8));
        return "Date: " + date + "\r\n"
                + "From: <" + config.from + ">\r\n"
                + "To: " + String.join(",\r\n ", config.recipients()) + "\r\n"
                + "Message-ID: <" + messageId + "@relaybridge.local>\r\n"
                + "Subject: " + encodedSubject("RelayBridge: " + kind) + "\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n"
                + "\r\n"
                + encodedBody + "\r\n";
    }

    /** Folded encoded words, each short enough for the 75-character limit and never splitting a character. */
    private static String encodedSubject(String subject) {
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        while (cursor < subject.length()) {
            int end = Math.min(subject.length(), cursor + 12);
            if (end < subject.length() && Character.isHighSurrogate(subject.charAt(end - 1))) end--;
            if (out.length() > 0) out.append("\r\n ");
            out.append("=?UTF-8?B?").append(base64(subject.substring(cursor, end))).append("?=");
            cursor = end;
        }
        return out.toString();
    }

    /** A server reply: its code and all of its lines. */
    private static final class Reply {
        final int code;
        final List<String> lines;

        Reply(int code, List<String> lines) {
            this.code = code;
            this.lines = lines;
        }

        /** Text after the reply code, upper-cased; empty for a bare code. */
        private static String keyword(String line) {
            return line.length() > 4 ? line.substring(4).toUpperCase(Locale.ROOT) : "";
        }

        /** Whether an EHLO reply announces an extension. */
        boolean has(String extension) {
            for (String line : lines) {
                if (keyword(line).split("[ =]", 2)[0].equals(extension)) return true;
            }
            return false;
        }

        /** Supported AUTH mechanisms announced by an EHLO reply, in server order. */
        Set<String> mechanisms() {
            Set<String> result = new LinkedHashSet<>();
            for (String line : lines) {
                String value = keyword(line);
                if (!value.startsWith("AUTH ") && !value.startsWith("AUTH=")) continue;
                for (String mechanism : value.substring(5).split("\\s+")) {
                    if (mechanism.equals("PLAIN") || mechanism.equals("LOGIN")) result.add(mechanism);
                }
            }
            return result;
        }
    }
}
