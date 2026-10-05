package ru.eyeone.relaybridge;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;

/** The two delivery channels: Telegram Bot API and email over SMTP. */
final class Senders {
    private static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    private static final int READ_TIMEOUT_MILLIS = 20_000;

    private Senders() { }

    /** A delivery failure with a text that is safe to show and store, and whether a retry can help. */
    static final class SendError extends Exception {
        final boolean retry;
        final String safe;

        SendError(boolean retry, String safe) {
            super(safe);
            this.retry = retry;
            this.safe = safe;
        }
    }

    /** Send one plain-text message of at most 4096 characters to the configured chat. */
    static void telegram(Config config, String text) throws Exception {
        // The token is part of the URL, so this URL must never reach a log or an error text.
        URL url = new URL("https://api.telegram.org/bot" + config.token + "/sendMessage");
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        try {
            byte[] request = new JSONObject().put("chat_id", config.chat).put("text", text)
                    .toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(request);
            }
            int status = connection.getResponseCode();
            if (status != 200) throw new SendError(status == 429 || status >= 500, "HTTP " + status + hint(status));
            try (InputStream in = connection.getInputStream()) {
                JSONObject response = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                if (!response.optBoolean("ok")) throw new SendError(false, "API отклонил сообщение");
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String hint(int status) {
        switch (status) {
            case 401:
            case 404:
                return " — проверьте BotToken";
            case 400:
            case 403:
                return " — проверьте ChatID, /start и доступ бота";
            case 429:
                return " — лимит сообщений; повторите позже";
            default:
                return "";
        }
    }

    /** Send one email and record the session in the SMTP diagnostic log. */
    static void email(Config config, String kind, String body, String id) throws Exception {
        long session = SmtpLog.begin();
        try {
            config.validateEmail();
            SmtpClient.send(config, kind, body, id, line -> SmtpLog.line(session, line));
        } catch (Exception failure) {
            SmtpLog.line(session, "Ошибка отправки: " + DeliveryErrors.describe(failure));
            throw failure;
        } finally {
            SmtpLog.persist();
        }
    }
}
