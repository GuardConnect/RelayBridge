package ru.eyeone.relaybridge;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.telephony.TelephonyManager;

/**
 * Captures incoming calls as text events: the number and the SIM being called. No audio is involved.
 * The last state and caller of every subscription are remembered so one call produces one event.
 */
public class CallReceiver extends BroadcastReceiver {
    private static final String KEY_STATE = "call-state-";
    private static final String KEY_LAST_CALLER = "call-last-";

    @Override public void onReceive(Context context, Intent intent) {
        if (!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) return;
        // Resolved immediately: which card is ringing can change before the queued handler runs.
        int subscription = SimCards.callSubscription(context, intent);
        CaptureExecutor.run(goAsync(), () -> {
            try {
                handle(context, intent, subscription);
            } catch (Exception failure) {
                Events.fault(context, "Ошибка обработки звонка");
            }
        });
    }

    @SuppressWarnings("deprecation") // EXTRA_INCOMING_NUMBER is still how the number reaches a non-dialer app.
    private static void handle(Context context, Intent intent, int subscription) throws Exception {
        Config config = Config.load(context);
        if (!config.enabled || !config.calls) return;
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        SharedPreferences prefs = Config.prefs(context);
        String sub = String.valueOf(subscription);

        if (!TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            rememberCallEnd(context, prefs, state, subscription);
            return;
        }
        if (!SimRules.accepts(config.callAllSims, config.callSubscriptions, subscription)) return;

        // Android sends two broadcasts with both permissions; only use the one with the number extra.
        boolean callLog = context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED;
        if (callLog && !intent.hasExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)) return;

        String number = PhoneNumbers.international(context,
                intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER), subscription);
        String fingerprint = TextTools.hash(number);
        boolean stillRinging = TelephonyManager.EXTRA_STATE_RINGING.equals(prefs.getString(KEY_STATE + sub, ""));
        if (stillRinging && fingerprint.equals(prefs.getString(KEY_LAST_CALLER + sub, ""))) return;
        prefs.edit().putString(KEY_LAST_CALLER + sub, fingerprint).putString(KEY_STATE + sub, state).apply();

        String sim = subscription >= 0 ? SimCards.label(context, subscription) : "SIM не определена";
        String sender = ContactNames.lookup(context, number);
        long now = System.currentTimeMillis();

        Events.Incoming event = new Events.Incoming("call-" + sub + "-" + now + "-" + fingerprint, Events.KIND_CALL);
        event.title = "Входящий звонок · " + sender;
        event.message = "От: " + sender + "\nНомер: " + number;
        event.data = ContactNames.callData(sender, number, sim);
        event.number = number;
        event.sim = sim;
        event.sender = sender;
        Events.capture(context, event);
    }

    /** Record that ringing is over, so the next call from the same number is a new event. */
    private static void rememberCallEnd(Context context, SharedPreferences prefs, String state, int subscription) {
        if (TelephonyManager.EXTRA_STATE_IDLE.equals(state) || TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state))
            prefs.edit().putString(KEY_STATE + subscription, state).apply();
        if (subscription >= 0) return;
        // A generic IDLE broadcast has no SIM: reset only subscriptions confirmed idle.
        TelephonyManager telephony = context.getSystemService(TelephonyManager.class);
        if (telephony == null) return;
        for (SimCards.Card card : SimCards.active(context)) {
            try {
                if (telephony.createForSubscriptionId(card.id).getCallStateForSubscription()
                        == TelephonyManager.CALL_STATE_IDLE)
                    prefs.edit().putString(KEY_STATE + card.id, TelephonyManager.EXTRA_STATE_IDLE).apply();
            } catch (SecurityException | UnsupportedOperationException unavailable) {
                // This card cannot be queried; leave its state untouched.
            }
        }
    }
}
