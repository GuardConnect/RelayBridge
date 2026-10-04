package ru.eyeone.relaybridge;
import org.json.JSONObject;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
final class Senders {
    static final class SendError extends Exception { final boolean retry;final String safe;SendError(boolean r,String s){super(s);retry=r;safe=s;} }
    static void telegram(Config c,String text) throws Exception {
        HttpsURLConnection connection=(HttpsURLConnection)new URL("https://api.telegram.org/bot"+c.token+"/sendMessage").openConnection();
        connection.setConnectTimeout(15000);connection.setReadTimeout(20000);connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
        try {
            byte[] bytes=new JSONObject().put("chat_id",c.chat).put("text",text).toString().getBytes(StandardCharsets.UTF_8);
            try(java.io.OutputStream out=connection.getOutputStream()) {out.write(bytes);}
            int code=connection.getResponseCode();
            if(code!=200) throw new SendError(code==429 || code>=500,"HTTP "+code+(code==401||code==404?" — проверьте BotToken":code==400||code==403?" — проверьте ChatID, /start и доступ бота":code==429?" — лимит сообщений; повторите позже":""));
            try(InputStream in=connection.getInputStream()) {
                JSONObject j=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));
                if(!j.optBoolean("ok")) throw new SendError(false,"API отклонил сообщение");
            }
        } finally {connection.disconnect();}
    }
    static void email(Config c,String kind,String body,String id) throws Exception {
        long session=SmtpLog.begin();
        try{
            c.validateEmail();
            SmtpClient.send(c,kind,body,id,line->SmtpLog.line(session,line));
        }catch(Exception e){SmtpLog.line(session,"Ошибка отправки: "+DeliveryErrors.describe(e));throw e;}
        finally{SmtpLog.persist();}
    }
}
