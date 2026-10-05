package ru.eyeone.relaybridge;

import android.content.BroadcastReceiver;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Runs broadcast handling off the main thread, one event at a time and in arrival order. */
final class CaptureExecutor {
    private static final ExecutorService pool = Executors.newSingleThreadExecutor();

    private CaptureExecutor() { }

    /** Run {@code action} and then release the broadcast, whatever the outcome. */
    static void run(BroadcastReceiver.PendingResult result, Runnable action) {
        pool.execute(() -> {
            try {
                action.run();
            } finally {
                result.finish();
            }
        });
    }
}
