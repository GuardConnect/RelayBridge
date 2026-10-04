package ru.eyeone.relaybridge;
import org.junit.Test;
import java.net.*;
import java.io.IOException;
import javax.net.ssl.SSLHandshakeException;
import static org.junit.Assert.*;
public class DeliveryErrorsTest {
    @Test public void describesNestedDnsFailureWithoutLeakingCredentials() {
        String secret="https://api.telegram.org/bot123:SECRET/sendMessage";
        IOException wrapper=new IOException(secret,new UnknownHostException(secret));
        String text=DeliveryErrors.describe(wrapper);
        assertTrue(text.contains("DNS"));assertFalse(text.contains("SECRET"));
    }
    @Test public void describesTlsAndTimeout() {
        assertTrue(DeliveryErrors.describe(new SSLHandshakeException("secret")).contains("TLS"));
        assertTrue(DeliveryErrors.describe(new SocketTimeoutException("secret")).contains("вовремя"));
    }
    @Test public void genericExceptionDoesNotExposeMessage() {
        assertFalse(DeliveryErrors.describe(new Exception("BotToken secret")).contains("secret"));
    }
}
