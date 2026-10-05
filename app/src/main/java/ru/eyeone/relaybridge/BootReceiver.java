package ru.eyeone.relaybridge;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.service.notification.NotificationListenerService;

/** Resumes waiting deliveries and the notification listener after a reboot. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        CaptureExecutor.run(goAsync(), () -> {
            Events.recover(context);
            NotificationListenerService.requestRebind(new ComponentName(context, RelayListener.class));
        });
    }
}
