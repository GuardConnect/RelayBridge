package ru.eyeone.relaybridge;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
final class Indicator {
    static boolean allowed(Context c){
        if(c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return false;
        NotificationManager manager=c.getSystemService(NotificationManager.class);if(!manager.areNotificationsEnabled())return false;
        NotificationChannel channel=manager.getNotificationChannel("relay-status");return channel==null||channel.getImportance()!=NotificationManager.IMPORTANCE_NONE;
    }
    static void update(Context c,boolean active) {
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("relay-status","Состояние пересылки",NotificationManager.IMPORTANCE_LOW));
        if(!active) {nm.cancel(1);return;}
        if(!allowed(c)){nm.cancel(1);return;}
        PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getBroadcast(c,1,new Intent(c,PauseReceiver.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        try{nm.notify(1,new Notification.Builder(c,"relay-status").setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("RelayBridge включён").setContentText("SMS, звонки и выбранные уведомления пересылаются")
            .setContentIntent(open).setOngoing(true).setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(new Notification.Action.Builder(null,"Остановить",stop).build()).build());}catch(SecurityException ignored){}
    }
    static void stop(Context c) throws Exception {Config v=Config.load(c);v.enabled=false;v.save(c);Events.clear(c);update(c,false);}
}
