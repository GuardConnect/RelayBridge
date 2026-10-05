package ru.eyeone.relaybridge;

import android.content.Context;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

/** Entry point of the pipeline: captured event → rendered text → encrypted queue → scheduled delivery. */
final class Events {
    static final String KIND_SMS = "SMS";
    static final String KIND_CALL = "Входящий звонок";
    static final String KIND_NOTIFICATION = "Уведомление";

    private static final String WORK_TAG = "relay-delivery";
    private static final String KEY_FAULT = "fault";
    private static final String KEY_LAST_CAPTURE = "last-capture";

    private Events() { }

    /** Everything known about one captured event. Unavailable fields stay empty. */
    static final class Incoming {
        /** De-duplication key: an event with a key already in the queue is ignored. */
        final String key;
        final String kind;
        String title = "", message = "", data = "", number = "", sim = "", app = "", pkg = "", sender = "";

        Incoming(String key, String kind) {
            this.key = key;
            this.kind = kind;
        }
    }

    /**
     * Render the event with the current template, store it and schedule its delivery.
     *
     * @return true when the event was added to the queue
     */
    static synchronized boolean capture(Context context, Incoming event) {
        try {
            Config config = Config.load(context);
            if (!config.enabled || !Indicator.allowed(context)) return false;
            if (!config.telegram && !config.email) return false;
            long now = System.currentTimeMillis();
            String body = MessageTemplate.render(config.messageTemplate, MessageTemplate.values(event.kind,
                    event.title, event.message, event.data, event.number, event.sim, event.app, event.pkg,
                    event.sender, now));
            long id = QueueDb.get(context).add(event.key, event.kind, body, config);
            if (id <= 0) return false;
            Config.prefs(context).edit().putLong(KEY_LAST_CAPTURE, now).apply();
            schedule(context, id);
            return true;
        } catch (IllegalArgumentException invalidTemplate) {
            fault(context, "Формат сообщения: " + invalidTemplate.getMessage());
            return false;
        } catch (Exception failure) {
            fault(context, "Не удалось добавить событие в очередь");
            return false;
        }
    }

    /** Schedule delivery unless it is already scheduled or running. */
    static void schedule(Context context, long id) {
        schedule(context, id, ExistingWorkPolicy.KEEP);
    }

    /** Schedule delivery anew, resetting the attempt counter and back-off of a previous run. */
    static void retry(Context context, long id) {
        schedule(context, id, ExistingWorkPolicy.REPLACE);
    }

    static String workName(long id) {
        return "event-" + id;
    }

    private static void schedule(Context context, long id, ExistingWorkPolicy policy) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DeliveryWorker.class)
                .setInputData(new Data.Builder().putLong(DeliveryWorker.INPUT_ID, id).build())
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(false)
                        .build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(WORK_TAG)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(workName(id), policy, request);
    }

    /** After a start of the process or a reboot: drop expired events and reschedule the waiting ones. */
    static void recover(Context context) {
        try {
            QueueDb db = QueueDb.get(context);
            db.prune();
            if (!Config.load(context).enabled) return;
            for (long id : db.pending()) schedule(context, id);
        } catch (Exception failure) {
            fault(context, "Ошибка открытия настроек/очереди");
        }
    }

    /** Remember a problem to be shown on the overview screen. */
    static void fault(Context context, String message) {
        Config.prefs(context).edit().putString(KEY_FAULT, message).apply();
    }

    /** Cancel all deliveries and erase the queue together with the history. */
    static void clear(Context context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG);
        QueueDb.get(context).clear();
    }
}
