package ru.eyeone.relaybridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The "stop" action of the status notification. */
public class PauseReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        try {
            Indicator.stop(context);
        } catch (Exception failure) {
            Events.fault(context, "Не удалось остановить пересылку");
        }
    }
}
