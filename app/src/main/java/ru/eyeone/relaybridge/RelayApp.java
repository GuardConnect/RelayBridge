package ru.eyeone.relaybridge;

import android.app.Application;

public class RelayApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        SmtpLog.init(this);
        Events.recover(this);
        try {
            Indicator.update(this, Config.load(this).enabled);
        } catch (Exception unreadable) {
            // Unreadable settings mean forwarding is off; the overview screen reports the problem.
        }
    }
}
