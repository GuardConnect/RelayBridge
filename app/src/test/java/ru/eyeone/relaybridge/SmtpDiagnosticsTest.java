package ru.eyeone.relaybridge;
import org.junit.Test;
import static org.junit.Assert.*;
public class SmtpDiagnosticsTest {
 @Test public void numericCodePreserved(){assertTrue(SmtpDiagnostics.auth("534-5.7.90 Application-specific password required").contains("534 / 5.7.90"));}
 @Test public void noRawServerText(){assertTrue(SmtpDiagnostics.auth("534 5.7.14 Please login through browser").contains("534 / 5.7.14"));}
 @Test public void serverResponseCannotLeakSecrets(){String result=SmtpDiagnostics.auth("535 5.7.80 rejected private@example.com my-secret-password");assertTrue(result.contains("535 / 5.7.80"));assertFalse(result.contains("private@example"));assertFalse(result.contains("my-secret"));}
 @Test public void temporaryAndUnknown(){assertTrue(SmtpDiagnostics.auth("454 4.7.0 Temporary failure").contains("временный"));assertTrue(SmtpDiagnostics.auth(null).contains("авторизация отклонена"));}
}
