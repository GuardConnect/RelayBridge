package ru.eyeone.relaybridge;
import android.content.*;
public class PauseReceiver extends BroadcastReceiver {
    public void onReceive(Context c,Intent i) {try {Indicator.stop(c);} catch(Exception e){Events.fault(c,"Не удалось остановить пересылку");}}
}
