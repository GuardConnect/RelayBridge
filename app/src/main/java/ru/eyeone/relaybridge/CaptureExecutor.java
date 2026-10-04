package ru.eyeone.relaybridge;
import android.content.BroadcastReceiver;
import java.util.concurrent.Executors;
final class CaptureExecutor {
    private static final java.util.concurrent.ExecutorService pool=Executors.newSingleThreadExecutor();
    static void run(BroadcastReceiver.PendingResult result,Runnable action) {
        pool.execute(()->{try{action.run();}finally{result.finish();}});
    }
}
