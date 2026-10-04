package ru.eyeone.relaybridge;
import org.junit.Test;
import static org.junit.Assert.*;
public class SmtpDiagnosticsTest {
 @Test public void gmailAppPasswordRequirementIsExplainedWithoutRawServerText(){
  String result=SmtpDiagnostics.auth("534 / 5.7.9","smtp.gmail.com","534-5.7.9 Application-specific password required. fixture-user@example.com fixture-secret");
  assertTrue(result.contains("Gmail требует пароль приложения"));assertFalse(result.contains("fixture-user"));assertFalse(result.contains("fixture-secret"));
 }
 @Test public void gmailBrowserRequirementIsDistinguishedFromAppPasswordRequirement(){
  assertTrue(SmtpDiagnostics.auth("534 / 5.7.9","smtp.gmail.com","534-5.7.9 Please log in with your web browser and then try again.").contains("подтверждения входа"));
  assertFalse(SmtpDiagnostics.auth("534 / 5.7.9","smtp.gmail.com","534 Unknown refusal").contains("Gmail требует"));
  assertFalse(SmtpDiagnostics.auth("534 / 5.7.9","smtp.example.com","534 Application-specific password required").contains("Gmail требует"));
 }
 @Test public void numericCodePreserved(){assertTrue(SmtpDiagnostics.auth("534-5.7.90 Application-specific password required").contains("534 / 5.7.90"));}
 @Test public void noRawServerText(){assertTrue(SmtpDiagnostics.auth("534 5.7.14 Please login through browser").contains("534 / 5.7.14"));}
 @Test public void serverResponseCannotLeakSecrets(){String result=SmtpDiagnostics.auth("535 5.7.80 rejected private@example.com my-secret-password");assertTrue(result.contains("535 / 5.7.80"));assertFalse(result.contains("private@example"));assertFalse(result.contains("my-secret"));}
 @Test public void temporaryAndUnknown(){assertTrue(SmtpDiagnostics.auth("454 4.7.0 Temporary failure").contains("временный"));assertTrue(SmtpDiagnostics.auth(null).contains("авторизация отклонена"));}
}
