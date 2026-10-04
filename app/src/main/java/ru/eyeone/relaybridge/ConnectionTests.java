package ru.eyeone.relaybridge;
import java.util.UUID;
import java.util.concurrent.Executors;
final class ConnectionTests {
    private static final java.util.concurrent.ExecutorService pool=Executors.newFixedThreadPool(2);
    static volatile String telegramStatus="Telegram: тест ещё не выполнялся.";
    static volatile String emailStatus="Email: тест ещё не выполнялся.";
    static volatile boolean telegramRunning, emailRunning;
    static String testMessage(boolean telegram) {
        return "RelayBridge "+BuildConfig.VERSION_NAME+" — тест "+(telegram?"Telegram\nСоединение с Bot API и отправка в указанный ChatID работают.":"SMTP\nSMTP-сервер принял это тестовое письмо.");
    }
    static synchronized boolean telegram(Config c) {
        if(telegramRunning) return false;
        c.validateTelegram();
        telegramRunning=true;telegramStatus="Telegram: отправляю тестовое сообщение…";
        pool.execute(()->{
            try { Senders.telegram(c,testMessage(true));
                telegramStatus="Telegram: API подтвердил отправку. Проверьте тестовое сообщение в чате.";
            } catch(Exception e) {telegramStatus="Telegram: "+DeliveryErrors.describe(e);}
            finally {telegramRunning=false;}
        });
        return true;
    }
    static synchronized boolean email(Config c) {
        if(emailRunning) return false;
        c.validateEmail();
        emailRunning=true;emailStatus="Email: подключаюсь к SMTP и отправляю тест…";
        pool.execute(()->{
            try { Senders.email(c,"Тест email",testMessage(false),"test-"+UUID.randomUUID());
                emailStatus="Email: SMTP-сервер принял письмо. Проверьте «Входящие» и «Спам».";
            } catch(Exception e) {emailStatus="Email: "+DeliveryErrors.describe(e);}
            finally {emailRunning=false;}
        });
        return true;
    }
}
