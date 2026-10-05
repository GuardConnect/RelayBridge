package ru.eyeone.relaybridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;

/** Captures new incoming SMS. Old messages are never read. */
public class SmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        CaptureExecutor.run(goAsync(), () -> handle(context, intent));
    }

    private static void handle(Context context, Intent intent) {
        try {
            Config config = Config.load(context);
            if (!config.enabled || !config.sms) return;

            int subscription = SimCards.smsSubscription(context, intent);
            if (!SimRules.accepts(config.smsAllSims, config.smsSubscriptions, subscription)) {
                if (subscription < 0)
                    Events.fault(context, "SMS получено, но SIM не определена. Проверьте выбор SIM для SMS; режим «Все SIM» принимает такие сообщения.");
                return;
            }

            SmsPayload payload = SmsPayload.decode(intent);
            String text = payload.body();
            String number = PhoneNumbers.international(context, payload.parts[0].getOriginatingAddress(), subscription);
            String sender = ContactNames.lookup(context, number);
            String sim = SimCards.label(context, subscription);

            Events.Incoming event = new Events.Incoming(payload.key(subscription), Events.KIND_SMS);
            event.title = "SMS от " + sender;
            event.message = text;
            event.data = ContactNames.smsData(sender, number, sim, text);
            event.number = number;
            event.sim = sim;
            event.sender = sender;
            Events.capture(context, event);
        } catch (IllegalArgumentException unreadable) {
            // SmsPayload explains what exactly is wrong with the broadcast.
            Events.fault(context, unreadable.getMessage());
        } catch (Exception failure) {
            Events.fault(context, "Ошибка обработки SMS: сообщение не добавлено в очередь");
        }
    }
}
