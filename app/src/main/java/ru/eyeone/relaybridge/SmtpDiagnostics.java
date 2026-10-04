package ru.eyeone.relaybridge;
import java.util.regex.*;
/** Only numeric server codes are displayed; server text can contain personal data. */
final class SmtpDiagnostics {
    static String auth(String numeric,String host,String serverText) {
        String generic=auth(numeric);
        boolean gmail="smtp.gmail.com".equalsIgnoreCase(host)||"smtp.googlemail.com".equalsIgnoreCase(host);
        if(!gmail||!numeric.startsWith("534"))return generic;
        String text=serverText.toLowerCase(java.util.Locale.ROOT);
        String prefix=generic.substring(0,generic.indexOf(": ")+2);
        if(text.contains("application-specific password required")||text.contains("app password required"))
            return prefix+"Gmail требует пароль приложения. Создайте его в том же Google-аккаунте, который указан в SMTP-логине; обычный пароль аккаунта не подходит.";
        if(text.contains("log in with your web browser")||text.contains("login through your web browser"))
            return prefix+"Gmail требует подтверждения входа. Откройте свой Google-аккаунт в браузере и проверьте уведомления безопасности и доступ для приложения.";
        return generic;
    }
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
