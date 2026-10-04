package ru.eyeone.relaybridge;
import android.content.*;
import android.service.notification.NotificationListenerService;
public class BootReceiver extends BroadcastReceiver {
    public void onReceive(Context c,Intent i) {
        if(Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) {
            PendingResult pending=goAsync();CaptureExecutor.run(pending,()->{
            Events.recover(c);
            NotificationListenerService.requestRebind(new ComponentName(c,RelayListener.class));
            });
        }
    }
}
