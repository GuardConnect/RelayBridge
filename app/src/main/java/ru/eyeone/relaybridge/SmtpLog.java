package ru.eyeone.relaybridge;
import android.content.Context;
import java.util.ArrayDeque;
final class SmtpLog {
    private static final ArrayDeque<String> lines=new ArrayDeque<>();
    private static Context context;
    private static long sequence;
    static synchronized void init(Context c){context=c.getApplicationContext();try{String raw=Config.prefs(context).getString("smtp-log","");if(!raw.isEmpty())for(String line:Crypto.open(raw).split("\n"))append(line);}catch(Exception ignored){}}
    static synchronized long begin(){long id=++sequence;line(id,"Новая SMTP-сессия");return id;}
    static synchronized void line(long id,String value){append(android.text.format.DateFormat.format("HH:mm:ss",System.currentTimeMillis())+" #"+id+" "+value);}
    private static void append(String value){lines.addLast(value);while(lines.size()>200)lines.removeFirst();}
    static synchronized String snapshot(){return lines.isEmpty()?"Лог пуст. Запустите тест SMTP во вкладке Отправка.\n\nЛог содержит этапы и числовые ответы. AUTH-данные, адреса и письмо скрыты.":String.join("\n",lines);}
    static synchronized void persist(){if(context==null)return;try{Config.prefs(context).edit().putString("smtp-log",Crypto.seal(String.join("\n",lines))).apply();}catch(Exception ignored){}}
    static synchronized void clear(){lines.clear();if(context!=null)Config.prefs(context).edit().remove("smtp-log").apply();}
}
