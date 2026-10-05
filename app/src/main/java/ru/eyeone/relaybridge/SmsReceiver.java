package ru.eyeone.relaybridge;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
public class SmsReceiver extends BroadcastReceiver {
    public void onReceive(Context c,Intent i) {
        if(!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(i.getAction())) return;
        PendingResult pending=goAsync();CaptureExecutor.run(pending,()->{
        try {
            Config cfg=Config.load(c);if(!cfg.enabled || !cfg.sms) return;
            int subscription=SimCards.subscription(i);if(!SimRules.accepts(cfg.smsAllSims,cfg.smsSubscriptions,subscription))return;
            SmsMessage[] parts=Telephony.Sms.Intents.getMessagesFromIntent(i);if(parts.length==0) return;
            StringBuilder text=new StringBuilder();for(SmsMessage part:parts) text.append(part.getMessageBody());
            String number=PhoneNumbers.international(c,parts[0].getOriginatingAddress(),subscription);String sub=String.valueOf(subscription);
            String key=TextTools.hash("sms|"+sub+"|"+number+"|"+parts[0].getTimestampMillis()+"|"+text);
            String sender=ContactNames.lookup(c,number);String sim=SimCards.label(c,subscription);String data=ContactNames.smsData(sender,number,sim,text.toString());
            Events.capture(c,key,"SMS","SMS от "+sender,text.toString(),data,number,sim,"","",sender,false);
        } catch(Exception e) {Events.fault(c,"Ошибка обработки SMS");}
        });
    }
}
