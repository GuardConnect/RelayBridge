package ru.eyeone.relaybridge;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;
final class Config {
    boolean enabled, sms, calls, pushes, telegram, email, ongoing;
    String token="", chat="", host="", user="", password="", from="", to="", tls="STARTTLS", auth="AUTO";
    String messageTemplate=MessageTemplate.DEFAULT;
    int port=587;
    boolean smsAllSims=true,callAllSims=true;
    final Set<Integer> callSubscriptions=new HashSet<>();
    final Set<Integer> smsSubscriptions=new HashSet<>();
    final Set<String> apps=new HashSet<>();
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("relay", Context.MODE_PRIVATE); }
    static Config load(Context c) throws Exception {
        Config v=new Config(); String raw=prefs(c).getString("config", "");
        if(raw.isEmpty()) return v;
        JSONObject j=new JSONObject(Crypto.open(raw));
        v.enabled=j.optBoolean("enabled") && Consent.accepted(c) && Indicator.allowed(c); v.sms=j.optBoolean("sms",true); v.calls=j.optBoolean("calls",true);
        v.pushes=j.optBoolean("pushes",true); v.telegram=j.optBoolean("telegram"); v.email=j.optBoolean("email"); v.ongoing=j.optBoolean("ongoing");
        v.token=j.optString("token"); v.chat=j.optString("chat"); v.host=j.optString("host","");
        v.user=j.optString("user"); v.password=j.optString("password"); v.from=j.optString("from"); v.to=j.optString("to");
        v.auth=j.optString("auth","AUTO");v.tls=j.optString("tls","STARTTLS"); v.port=j.optInt("port",587);
        if(v.auth.equals("XOAUTH2")){v.auth="AUTO";v.password="";v.email=false;}
        v.messageTemplate=j.optString("messageTemplate",MessageTemplate.DEFAULT);
        v.callAllSims=j.optBoolean("callAllSims",true);
        JSONArray callSims=j.optJSONArray("callSubscriptions");if(callSims!=null)for(int n=0;n<callSims.length();n++){int id=callSims.optInt(n,-1);if(id>=0)v.callSubscriptions.add(id);}
        v.smsAllSims=j.optBoolean("smsAllSims",true);
        JSONArray sims=j.optJSONArray("smsSubscriptions");if(sims!=null)for(int n=0;n<sims.length();n++){int id=sims.optInt(n,-1);if(id>=0)v.smsSubscriptions.add(id);}
        JSONArray a=j.optJSONArray("apps"); if(a!=null) for(int i=0;i<a.length();i++) v.apps.add(a.getString(i));
        return v;
    }
    void save(Context c) throws Exception {
        JSONObject j=new JSONObject();
        j.put("enabled",enabled).put("sms",sms).put("calls",calls).put("pushes",pushes).put("telegram",telegram).put("email",email).put("ongoing",ongoing)
            .put("token",token).put("chat",chat).put("host",host).put("user",user).put("password",password).put("from",from).put("to",to).put("auth",auth).put("tls",tls).put("port",port).put("apps",new JSONArray(apps)).put("smsAllSims",smsAllSims).put("smsSubscriptions",new JSONArray(smsSubscriptions)).put("callAllSims",callAllSims).put("callSubscriptions",new JSONArray(callSubscriptions)).put("messageTemplate",messageTemplate);
        if(!prefs(c).edit().putString("config",Crypto.seal(j.toString())).commit()) throw new Exception("Не удалось сохранить настройки");
    }
    String deliveryJson() throws Exception {
        return new JSONObject().put("token",token).put("chat",chat).put("host",host).put("port",port)
            .put("user",user).put("password",password).put("from",from).put("to",to).put("auth",auth).put("tls",tls).toString();
    }
    static Config deliveryConfig(String raw) throws Exception {
        JSONObject j=new JSONObject(raw);Config c=new Config();c.token=j.getString("token");c.chat=j.getString("chat");
        c.host=j.getString("host");c.port=j.getInt("port");c.user=j.getString("user");c.password=j.getString("password");
        c.from=j.getString("from");c.to=j.getString("to");c.auth=j.optString("auth","AUTO");c.tls=j.getString("tls");return c;
    }
    void validate() {
        if(!telegram && !email) throw new IllegalArgumentException("Включите Telegram и/или email");
        validateSelected();
    }
    void validateSelected() {
        MessageTemplate.validate(messageTemplate);
        if(telegram) validateTelegram();
        if(email) validateEmail();
    }
    void validateTelegram() {
        if(!token.matches("[0-9]+:[A-Za-z0-9_-]+")) throw new IllegalArgumentException("BotToken должен иметь вид 123456789:ABC…");
        if(!chat.matches("-?[0-9]+|@[A-Za-z][A-Za-z0-9_]{3,}")) throw new IllegalArgumentException("ChatID: числовой ID чата или @имя канала");
    }
    void validateEmail() {
        if(host.isBlank() || (host.contains(":")&&!host.matches("[0-9a-fA-F:]+")) || host.contains("/") || host.matches(".*\\s.*") || port<1 || port>65535)
            throw new IllegalArgumentException("SMTP: укажите имя сервера и порт 1–65535");
        if(!tls.equals("STARTTLS") && !tls.equals("SSL")) throw new IllegalArgumentException("SMTP: выберите STARTTLS или SSL");
        if(!java.util.Set.of("AUTO","LOGIN","PLAIN","NONE").contains(auth))throw new IllegalArgumentException("SMTP: неизвестный способ авторизации");
        if(tls.equals("NONE")&&!auth.equals("NONE"))throw new IllegalArgumentException("Без TLS разрешена только отправка без авторизации (SMTP relay)");
        if(!auth.equals("NONE")&&(user.isBlank() || password.isEmpty())) throw new IllegalArgumentException("SMTP: нужны логин и пароль");
        if(!address(from) || to.isBlank() || java.util.Arrays.stream(to.split("[,;]",-1)).anyMatch(a->!address(a.trim()))) throw new IllegalArgumentException("Укажите корректные email отправителя и получателя");
    }
    static boolean address(String s) { return s!=null && s.length()<=254 && s.matches("[^\\s\\p{Cntrl}<>@,;]+@[^\\s\\p{Cntrl}<>@,;]+\\.[^\\s\\p{Cntrl}<>@,;]+"); }
}
