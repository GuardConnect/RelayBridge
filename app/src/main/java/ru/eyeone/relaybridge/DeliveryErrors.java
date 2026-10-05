package ru.eyeone.relaybridge;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;

/** Turns exceptions into fixed user texts. Exception messages may contain secrets and are never shown. */
final class DeliveryErrors {
    private static final int MAX_CAUSE_DEPTH = 12;

    private DeliveryErrors() { }

    static String describe(Exception error) {
        if (error instanceof Senders.SendError) return ((Senders.SendError) error).safe;
        Throwable current = error;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof UnknownHostException)
                return "Не удалось найти сервер (DNS). Проверьте интернет, VPN и имя SMTP-сервера.";
            if (current instanceof SocketTimeoutException)
                return "Сервер не ответил вовремя. Проверьте сеть и попробуйте снова.";
            if (current instanceof SSLException)
                return "Ошибка защищённого TLS-соединения. Проверьте дату телефона, сервер и порт.";
            if (current instanceof ConnectException || current instanceof NoRouteToHostException)
                return "Нет соединения с сервером. Проверьте интернет, VPN и доступность порта.";
            Throwable cause = current.getCause();
            if (cause == current) break;
            current = cause;
        }
        return "Не удалось отправить сообщение. Проверьте сеть и реквизиты; повторите тест.";
    }
}
