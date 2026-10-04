package ru.eyeone.relaybridge;
import java.util.regex.*;
/** Only numeric server codes are displayed; server text can contain personal data. */
final class SmtpDiagnostics {
    static String auth(String response) {
        String input=response==null?"":response;
        Matcher code=Pattern.compile("(?<![0-9])([45][0-9]{2})(?=[ \r\n-]|$)").matcher(input);
        Matcher detail=Pattern.compile("(?<![0-9])([45]\\.[0-9]{1,3}\\.[0-9]{1,3})(?![0-9.])").matcher(input);
        String smtp=code.find()?code.group(1):"", enhanced=detail.find()?detail.group(1):"";
        String prefix="SMTP"+(smtp.isEmpty()?"":" "+smtp)+(enhanced.isEmpty()?"":" / "+enhanced)+": ";
        if(smtp.equals("454")||enhanced.startsWith("4."))return prefix+"временный отказ авторизации сервера. Повторите тест позже.";
        if(smtp.equals("535"))return prefix+"сервер отклонил SMTP-вход. Проверьте логин, пароль, разрешение SMTP у почтового провайдера и выбранный способ авторизации.";
        return prefix+"авторизация отклонена. Проверьте разрешение SMTP-входа у провайдера и способ авторизации; поддерживаются PLAIN/LOGIN через TLS.";
    }
}
