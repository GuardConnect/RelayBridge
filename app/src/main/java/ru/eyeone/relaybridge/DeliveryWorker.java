package ru.eyeone.relaybridge;

import android.content.Context;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Delivers one queued event to every channel that is still pending.
 * Channels are independent: a failure of one never repeats or blocks the other.
 */
public class DeliveryWorker extends Worker {
    static final String INPUT_ID = "id";

    /** Telegram accepts up to 4096 characters per message; longer texts are sent in parts. */
    private static final int TELEGRAM_CHUNK = 4000;
    /** After this many runs a temporary failure becomes final. */
    private static final int MAX_ATTEMPTS = 10;

    private QueueDb db;
    private long id;

    public DeliveryWorker(Context context, WorkerParameters parameters) {
        super(context, parameters);
    }

    @Override public Result doWork() {
        Context context = getApplicationContext();
        id = getInputData().getLong(INPUT_ID, -1);
        db = QueueDb.get(context);
        try {
            if (!Consent.accepted(context) || !Indicator.allowed(context)) return Result.success();
            Config current = Config.load(context);
            QueueDb.Event event = db.event(id);
            // Waiting events stay in the queue while forwarding is off; recover() schedules them again.
            if (event == null || !current.enabled) return Result.success();

            StringBuilder errors = new StringBuilder();
            boolean retry = false;

            if (event.tg == QueueDb.PENDING) {
                if (!current.telegram) {
                    db.update(id, "tg", QueueDb.DISABLED);
                } else {
                    try {
                        if (!sendTelegram(context, event)) return Result.success();
                        db.update(id, "tg", QueueDb.DELIVERED);
                    } catch (Exception failure) {
                        retry |= failed("tg", failure);
                        errors.append("Telegram: ").append(DeliveryErrors.describe(failure)).append(". ");
                    }
                }
            }

            if (event.mail == QueueDb.PENDING) {
                if (!current.email) {
                    db.update(id, "mail", QueueDb.DISABLED);
                } else {
                    try {
                        if (cancelled(context)) return Result.success();
                        // A stable Message-ID lets the recipient's server recognise a repeated attempt.
                        Senders.email(event.route, event.kind, event.body, "relay-" + TextTools.hash(event.body));
                        db.update(id, "mail", QueueDb.DELIVERED);
                    } catch (Exception failure) {
                        retry |= failed("mail", failure);
                        errors.append("Email: ").append(DeliveryErrors.describe(failure)).append(". ");
                    }
                }
            }

            db.error(id, errors.toString());
            db.redactDone(id);
            return retry ? Result.retry() : Result.success();
        } catch (Exception failure) {
            Events.fault(context, "Ошибка чтения зашифрованной очереди");
            return Result.failure();
        }
    }

    /**
     * Send the text in parts, remembering the position so that a retry continues instead of repeating.
     *
     * @return false when delivery was cancelled before the whole text was sent
     */
    private boolean sendTelegram(Context context, QueueDb.Event event) throws Exception {
        int position = event.pos;
        while (position < event.body.length()) {
            if (cancelled(context)) return false;
            int end = TextTools.chunkEnd(event.body, position, TELEGRAM_CHUNK);
            Senders.telegram(event.route, event.body.substring(position, end));
            position = end;
            db.update(id, "pos", position);
        }
        return true;
    }

    /** The work was stopped, the event was deleted, or forwarding was switched off meanwhile. */
    private boolean cancelled(Context context) throws Exception {
        return isStopped() || !db.exists(id) || !Config.load(context).enabled;
    }

    /**
     * Decide what happens to a channel after a failure.
     *
     * @return true when the whole work should be retried; otherwise the channel is marked as failed
     */
    private boolean failed(String channel, Exception failure) {
        boolean temporary = !(failure instanceof Senders.SendError) || ((Senders.SendError) failure).retry;
        boolean retry = temporary && getRunAttemptCount() < MAX_ATTEMPTS - 1;
        if (!retry) db.update(id, channel, QueueDb.FAILED);
        return retry;
    }
}
