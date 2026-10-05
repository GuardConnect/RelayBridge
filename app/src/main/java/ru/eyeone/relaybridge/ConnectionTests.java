package ru.eyeone.relaybridge;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Manual connection tests started from the settings screen; the screen polls the status fields. */
final class ConnectionTests {
    private static final ExecutorService pool = Executors.newFixedThreadPool(2);

    static volatile String telegramStatus = "Telegram: тест ещё не выполнялся.";
    static volatile String emailStatus = "Email: тест ещё не выполнялся.";
    static volatile boolean telegramRunning, emailRunning;

    private ConnectionTests() { }

    static String testMessage(boolean telegram) {
        return "RelayBridge " + BuildConfig.VERSION_NAME + " — тест " + (telegram
                ? "Telegram\nСоединение с Bot API и отправка в указанный ChatID работают."
                : "SMTP\nSMTP-сервер принял это тестовое письмо.");
    }

    /**
     * Send a test message with the given, not necessarily saved, settings.
     *
     * @return false when a Telegram test is already running
     * @throws IllegalArgumentException when the Telegram settings are invalid
     */
    static synchronized boolean telegram(Config config) {
        if (telegramRunning) return false;
        config.validateTelegram();
        telegramRunning = true;
        telegramStatus = "Telegram: отправляю тестовое сообщение…";
        pool.execute(() -> {
            try {
                Senders.telegram(config, testMessage(true));
                telegramStatus = "Telegram: API подтвердил отправку. Проверьте тестовое сообщение в чате.";
            } catch (Exception failure) {
                telegramStatus = "Telegram: " + DeliveryErrors.describe(failure);
            } finally {
                telegramRunning = false;
            }
        });
        return true;
    }

    /**
     * Send a test email with the given, not necessarily saved, settings.
     *
     * @return false when an email test is already running
     * @throws IllegalArgumentException when the SMTP settings are invalid
     */
    static synchronized boolean email(Config config) {
        if (emailRunning) return false;
        config.validateEmail();
        emailRunning = true;
        emailStatus = "Email: подключаюсь к SMTP и отправляю тест…";
        pool.execute(() -> {
            try {
                Senders.email(config, "Тест email", testMessage(false), "test-" + UUID.randomUUID());
                emailStatus = "Email: SMTP-сервер принял письмо. Проверьте «Входящие» и «Спам».";
            } catch (Exception failure) {
                emailStatus = "Email: " + DeliveryErrors.describe(failure);
            } finally {
                emailRunning = false;
            }
        });
        return true;
    }
}
