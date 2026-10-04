package ru.eyeone.relaybridge;
import android.content.Context;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
public class DeliveryWorker extends Worker {
    public DeliveryWorker(Context c,WorkerParameters p) { super(c,p); }
    public Result doWork() {
        Context c=getApplicationContext();long id=getInputData().getLong("id",-1);QueueDb db=QueueDb.get(c);
        try {
            if(!Consent.accepted(c)||!Indicator.allowed(c))return Result.success();
            Config current=Config.load(c);QueueDb.Event event=db.event(id);
            if(event==null) return Result.success();
            Config cfg=event.route;
            if(!current.enabled && !event.kind.equals("Тест")) return Result.success();
            boolean retry=false;StringBuilder errors=new StringBuilder();
            if(event.tg==0) {
                if(!current.telegram) db.update(id,"tg",2);
                else try {
                    int pos=event.pos;
                    while(pos<event.body.length()) {
                        if(isStopped() || !db.exists(id) || (!event.kind.equals("Тест") && !Config.load(c).enabled)) return Result.success();
                        int end=TextTools.chunkEnd(event.body,pos,4000);
                        Senders.telegram(cfg,event.body.substring(pos,end));pos=end;db.update(id,"pos",pos);
                    }
                    db.update(id,"tg",1);
                } catch(Senders.SendError e) { retry|=failed(db,id,"tg",e.retry);errors.append("Telegram: ").append(e.safe).append(". "); }
                  catch(Exception e) {retry|=failed(db,id,"tg",true);errors.append("Telegram: ").append(DeliveryErrors.describe(e)).append(". ");}
            }
            if(event.mail==0) {
                if(!current.email) db.update(id,"mail",2);
                else try {
                    if(isStopped() || !db.exists(id) || (!event.kind.equals("Тест") && !Config.load(c).enabled)) return Result.success();
                    Senders.email(cfg,event.kind,event.body,"relay-"+TextTools.hash(event.body));db.update(id,"mail",1);
                } catch(Senders.SendError e) {retry|=failed(db,id,"mail",e.retry);errors.append("Email: ").append(e.safe).append(". ");}
                  catch(Exception e) {retry|=failed(db,id,"mail",true);errors.append("Email: ").append(DeliveryErrors.describe(e)).append(". ");}
            }
            db.error(id,errors.toString());db.redactDone(id);
            return retry?Result.retry():Result.success();
        } catch(Exception e) { Events.fault(c,"Ошибка чтения зашифрованной очереди");return Result.failure(); }
    }
    private boolean failed(QueueDb db,long id,String channel,boolean temporary) {
        boolean retry=temporary && getRunAttemptCount()<9;
        if(!retry) db.update(id,channel,3);
        return retry;
    }
}
