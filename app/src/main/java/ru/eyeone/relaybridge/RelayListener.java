package ru.eyeone.relaybridge;
import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.content.ComponentName;
public class RelayListener extends NotificationListenerService {
    static volatile boolean connected;
    @Override public void onListenerConnected() { connected=true;Events.recover(this); }
    @Override public void onListenerDisconnected() { connected=false;try{requestRebind(new ComponentName(this,RelayListener.class));}catch(Exception ignored){} }
    @Override public void onDestroy() {connected=false;super.onDestroy();}
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            Config c=Config.load(this);String pkg=sbn.getPackageName();Notification n=sbn.getNotification();
            if(!c.enabled || !c.pushes || pkg.equals(getPackageName()) || !c.apps.contains(pkg)) return;
            if((n.flags & Notification.FLAG_GROUP_SUMMARY)!=0 || (!c.ongoing && sbn.isOngoing())) return;
            Bundle b=n.extras;String title=string(b.getCharSequence(Notification.EXTRA_TITLE));
            String text=string(b.getCharSequence(Notification.EXTRA_BIG_TEXT));
            if(text.isEmpty()) text=string(b.getCharSequence(Notification.EXTRA_TEXT));
            CharSequence[] lines=b.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if(text.isEmpty() && lines!=null) {StringBuilder s=new StringBuilder();for(CharSequence line:lines)s.append(line).append('\n');text=s.toString().trim();}
            if(text.isEmpty()) {
                android.os.Parcelable[] messages=b.getParcelableArray(Notification.EXTRA_MESSAGES);
                if(messages!=null) {
                    java.util.List<Notification.MessagingStyle.Message> parsed=Notification.MessagingStyle.Message.getMessagesFromBundleArray(messages);
                    if(!parsed.isEmpty()) text=string(parsed.get(parsed.size()-1).getText());
                }
            }
            if(title.isEmpty() && text.isEmpty()) {
                Events.fault(this,"Выбранное приложение опубликовало уведомление без доступного текста; Android может скрывать содержимое.");
                return;
            }
            String fp=TextTools.hash(title+"\n"+text),key=TextTools.hash(sbn.getKey());
            if(QueueDb.get(this).seen(key,fp)) return;
            String label=pkg;try {label=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception ignored){}
            if(!Events.capture(this,TextTools.hash(key+"|"+sbn.getPostTime()+"|"+fp),"Уведомление",title,text,label+" ("+pkg+")\n"+title+"\n"+text,"","",label,pkg,false))
                QueueDb.get(this).forget(key);
        } catch(Exception e) {Events.fault(this,"Ошибка обработки уведомления");}
    }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) {QueueDb.get(this).forget(TextTools.hash(sbn.getKey()));}
    private String string(CharSequence s) {return s==null?"":s.toString();}
}
