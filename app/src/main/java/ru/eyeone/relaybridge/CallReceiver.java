package ru.eyeone.relaybridge;
import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.telephony.TelephonyManager;
public class CallReceiver extends BroadcastReceiver {
    public void onReceive(Context c,Intent i) {
        if(!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(i.getAction())) return;
        int subscription=SimCards.callSubscription(c,i);
        PendingResult pending=goAsync();CaptureExecutor.run(pending,()->{
        try {
            Config cfg=Config.load(c);if(!cfg.enabled || !cfg.calls) return;
            String state=i.getStringExtra(TelephonyManager.EXTRA_STATE);
            SharedPreferences p=Config.prefs(c);
            String sub=String.valueOf(subscription);
            if(!TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                if(TelephonyManager.EXTRA_STATE_IDLE.equals(state) || TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state))
                    p.edit().putString("call-state-"+sub,state).apply();
                // A generic IDLE broadcast has no SIM: reset only subscriptions confirmed idle.
                if(subscription<0){TelephonyManager tm=c.getSystemService(TelephonyManager.class);
                    if(tm!=null)for(SimCards.Card card:SimCards.active(c))try{
                        if(tm.createForSubscriptionId(card.id).getCallStateForSubscription()==TelephonyManager.CALL_STATE_IDLE)
                            p.edit().putString("call-state-"+card.id,TelephonyManager.EXTRA_STATE_IDLE).apply();
                    }catch(SecurityException|UnsupportedOperationException ignored){}
                }
                return;
            }
            if(!SimRules.accepts(cfg.callAllSims,cfg.callSubscriptions,subscription))return;
            // Android sends two broadcasts with both permissions; only use the one with the number extra.
            if(c.checkSelfPermission(Manifest.permission.READ_CALL_LOG)==PackageManager.PERMISSION_GRANTED && !i.hasExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)) return;
            String number=i.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
            if(number==null || number.isBlank()) number="Скрыт или недоступен";
            long now=System.currentTimeMillis();
            String fingerprint=TextTools.hash(number);
            if(TelephonyManager.EXTRA_STATE_RINGING.equals(p.getString("call-state-"+sub,"")) && fingerprint.equals(p.getString("call-last-"+sub,""))) return;
            p.edit().putString("call-last-"+sub,fingerprint).putString("call-state-"+sub,state).apply();
            String sim=subscription>=0?SimCards.label(c,subscription):"SIM не определена";
            Events.capture(c,"call-"+sub+"-"+now+"-"+fingerprint,"Входящий звонок","Входящий звонок","Номер: "+number,SimRules.callData(sim,number),number,sim,"","",false);
        } catch(Exception e) { Events.fault(c,"Ошибка обработки звонка"); }
        });
    }
}
