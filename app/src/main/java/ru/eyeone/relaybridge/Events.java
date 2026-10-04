package ru.eyeone.relaybridge;
import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;
final class Events {
    static boolean capture(Context c,String key,String kind,String text,boolean test){
        return capture(c,key,kind,kind,text,text,"","","","",test);
    }
    static synchronized boolean capture(Context c,String key,String kind,String title,String message,String data,String number,String sim,String app,String pkg,boolean test) {
        return capture(c,key,kind,title,message,data,number,sim,app,pkg,"",test);
    }
    static synchronized boolean capture(Context c,String key,String kind,String title,String message,String data,String number,String sim,String app,String pkg,String sender,boolean test) {
        try {
            Config cfg=Config.load(c);
            if((!cfg.enabled || !Indicator.allowed(c)) && !test) return false;
            if(!cfg.telegram && !cfg.email) return false;
            String body=MessageTemplate.render(cfg.messageTemplate,MessageTemplate.values(kind,title,message,data,number,sim,app,pkg,sender,System.currentTimeMillis()));
            long id=QueueDb.get(c).add(key,kind,body,cfg);
            if(id>0) {
                Config.prefs(c).edit().putLong("last-capture",System.currentTimeMillis()).apply();
                schedule(c,id);return true;
            }
            return false;
        } catch(Exception e) { fault(c,e instanceof IllegalArgumentException?"Формат сообщения: "+e.getMessage():"Не удалось добавить событие в очередь");return false; }
    }
    static void schedule(Context c,long id) { schedule(c,id,ExistingWorkPolicy.KEEP); }
    static void retry(Context c,long id) { schedule(c,id,ExistingWorkPolicy.REPLACE); }
    private static void schedule(Context c,long id,ExistingWorkPolicy policy) {
        OneTimeWorkRequest w=new OneTimeWorkRequest.Builder(DeliveryWorker.class)
            .setInputData(new Data.Builder().putLong("id",id).build())
            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(false).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).addTag("relay-delivery").build();
        WorkManager.getInstance(c).enqueueUniqueWork("event-"+id,policy,w);
    }
    static void recover(Context c) {
        try { QueueDb.get(c).prune();if(Config.load(c).enabled) for(long id:QueueDb.get(c).pending()) schedule(c,id); }
        catch(Exception e) { fault(c,"Ошибка открытия настроек/очереди"); }
    }
    static void fault(Context c,String message) { Config.prefs(c).edit().putString("fault",message).apply(); }
    static void clear(Context c) { WorkManager.getInstance(c).cancelAllWorkByTag("relay-delivery");QueueDb.get(c).clear(); }
}
