package ru.eyeone.relaybridge;
import org.junit.Test;
import static org.junit.Assert.*;
public class ConfigValidationTest {
    private Config emailConfig() {
        Config c=new Config();c.host="smtp.example.com";c.email=true;c.user="test.sender@example.com";c.password="example-app-password";
        c.from=c.user;c.to="recipient@example.com";return c;
    }
    @Test public void emailAddressesIncludingLetterSValidate() {
        Config c=emailConfig();c.validate();
        assertTrue(Config.address("some.user+relay@example.com"));
        assertTrue(Config.address("notifications@example.co.uk"));
    }
    @Test public void rejectsWhitespaceAndHeaderInjection() {
        assertFalse(Config.address("a b@example.com"));
        assertFalse(Config.address("sender@example.com\r\nBcc: victim@example.com"));
        assertFalse(Config.address("two@example.com,second@example.com"));
        assertFalse(Config.address("no-domain"));
    }
    @Test public void telegramTestIgnoresIncompleteSmtp() {
        Config c=new Config();c.token="123456:ABC_def-ghi";c.chat="-1001234567890";
        c.host="smtp.example.com";c.email=true;c.password="";c.from="";c.to="";
        c.validateTelegram();
    }
    @Test public void smtpTestIgnoresIncompleteTelegram() {
        Config c=emailConfig();c.telegram=true;c.token="";c.chat="";
        c.validateEmail();
    }
    @Test public void acceptsChatAndChannelIds() {
        Config c=new Config();c.token="123456:ABC_def-ghi";
        for(String chat:new String[]{"123456","-100123456","@relay_channel"}){c.chat=chat;c.validateTelegram();}
    }
    @Test public void rejectsPlaintextEvenWithoutAuthentication() {
        Config c=emailConfig();c.tls="NONE";c.auth="NONE";
        assertThrows(IllegalArgumentException.class,c::validateEmail);
    }
    @Test public void supportsSecureRelayWithoutPassword() {
        Config c=emailConfig();c.auth="NONE";c.password="";c.user="";
        for(String tls:new String[]{"STARTTLS","SSL"}){c.tls=tls;c.validateEmail();}
    }
    @Test public void freshInstallDoesNotSelectDataSources() {
        Config c=new Config();assertFalse(c.enabled);assertFalse(c.sms);assertFalse(c.calls);assertFalse(c.pushes);
    }
    @Test public void rejectsInvalidPortAndPlaintextTls() {
        Config c=emailConfig();c.port=0;
        assertThrows(IllegalArgumentException.class,c::validateEmail);
        c.port=587;c.tls="NONE";
        assertThrows(IllegalArgumentException.class,c::validateEmail);
    }
}
