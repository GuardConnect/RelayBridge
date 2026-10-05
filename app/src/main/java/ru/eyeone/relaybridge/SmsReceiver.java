package ru.eyeone.relaybridge;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
public class SmsReceiver extends BroadcastReceiver {
    public void onReceive(Context c,Intent i) {
        if(!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(i.getAction())) return;
        PendingResult pending=goAsync();CaptureExecutor.run(pending,()->{
        try {
            Config cfg=Config.load(c);if(!cfg.enabled || !cfg.sms) return;
            int subscription=SimCards.smsSubscription(c,i);
            if(!SimRules.accepts(cfg.smsAllSims,cfg.smsSubscriptions,subscription)){
                if(subscription<0)Events.fault(c,"SMS получено, но SIM не определена. Проверьте выбор SIM для SMS; режим «Все SIM» принимает такие сообщения.");
                return;
            }
            SmsPayload payload=SmsPayload.decode(i);String text=payload.body();
            String number=PhoneNumbers.international(c,payload.parts[0].getOriginatingAddress(),subscription);
            String key=payload.key(subscription);
            String sender=ContactNames.lookup(c,number);String sim=SimCards.label(c,subscription);String data=ContactNames.smsData(sender,number,sim,text.toString());
            Events.capture(c,key,"SMS","SMS от "+sender,text.toString(),data,number,sim,"","",sender,false);
        } catch(IllegalArgumentException e) {Events.fault(c,e.getMessage());}
        catch(Exception e) {Events.fault(c,"Ошибка обработки SMS: сообщение не добавлено в очередь");}
        });
    }
}
