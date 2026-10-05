package ru.eyeone.relaybridge;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * The ongoing status notification. Forwarding is always visible:
 * without the ability to show this notification nothing is captured or delivered.
 */
final class Indicator {
    private static final String CHANNEL = "relay-status";
    private static final int NOTIFICATION_ID = 1;

    private Indicator() { }

    /** Whether the status notification can currently be shown. */
    static boolean allowed(Context context) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (!manager.areNotificationsEnabled()) return false;
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
        return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    /** Show or remove the status notification. */
    static void update(Context context, boolean active) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(
                new NotificationChannel(CHANNEL, "Состояние пересылки", NotificationManager.IMPORTANCE_LOW));
        if (!active || !allowed(context)) {
            manager.cancel(NOTIFICATION_ID);
            return;
        }
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent open = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), flags);
        PendingIntent stop = PendingIntent.getBroadcast(context, 1, new Intent(context, PauseReceiver.class), flags);
        Notification notification = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("RelayBridge включён")
                .setContentText("SMS, звонки и выбранные уведомления пересылаются")
                .setContentIntent(open)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(null, "Остановить", stop).build())
                .build();
        try {
            manager.notify(NOTIFICATION_ID, notification);
        } catch (SecurityException revoked) {
            // The permission was withdrawn between the check and the call.
        }
    }

    /** Switch forwarding off, drop everything still waiting and remove the notification. */
    static void stop(Context context) throws Exception {
        Config config = Config.load(context);
        config.enabled = false;
        config.save(context);
        Events.clear(context);
        update(context, false);
    }
}
