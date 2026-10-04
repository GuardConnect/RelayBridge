package ru.eyeone.relaybridge;
import org.junit.Test;
import static org.junit.Assert.*;
public class ConnectionTestsTest {
    @Test public void telegramTestUsesInstalledBuildVersion() {
        assertEquals("RelayBridge "+BuildConfig.VERSION_NAME+" — тест Telegram\nСоединение с Bot API и отправка в указанный ChatID работают.",ConnectionTests.testMessage(true));
    }
    @Test public void smtpTestUsesInstalledBuildVersion() {
        assertEquals("RelayBridge "+BuildConfig.VERSION_NAME+" — тест SMTP\nSMTP-сервер принял это тестовое письмо.",ConnectionTests.testMessage(false));
    }
}
