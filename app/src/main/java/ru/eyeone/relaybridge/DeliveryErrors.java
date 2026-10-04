package ru.eyeone.relaybridge;
import javax.net.ssl.SSLException;
import java.net.*;
final class DeliveryErrors {
    static String describe(Exception error) {
        if(error instanceof Senders.SendError) return ((Senders.SendError)error).safe;
        Throwable next=error;
        for(int depth=0;next!=null && depth<12;depth++) {
            if(next instanceof UnknownHostException) return "Не удалось найти сервер (DNS). Проверьте интернет, VPN и имя SMTP-сервера.";
            if(next instanceof SocketTimeoutException) return "Сервер не ответил вовремя. Проверьте сеть и попробуйте снова.";
            if(next instanceof SSLException) return "Ошибка защищённого TLS-соединения. Проверьте дату телефона, сервер и порт.";
            if(next instanceof ConnectException || next instanceof NoRouteToHostException) return "Нет соединения с сервером. Проверьте интернет, VPN и доступность порта.";
            Throwable cause=next.getCause();
            if(cause==next) break;
            next=cause;
        }
        return "Не удалось отправить сообщение. Проверьте сеть и реквизиты; повторите тест.";
    }
}
