package ru.eyeone.relaybridge;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Captures notifications of the applications selected by the user.
 * Android delivers every notification to a listener; everything not selected is dropped here, unread.
 *
 * Android calls the listener on the main thread. Reading settings, the database, encryption and
 * scheduling all happen on one background thread instead, in the order the notifications arrived.
 */
public class RelayListener extends NotificationListenerService {
    /** Whether Android currently has the listener bound; shown on the access screen. */
    static volatile boolean connected;

    private static final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "relay-notifications");
        thread.setDaemon(true);
        return thread;
    });

    // Reconnection. A listener that stayed connected is rebound at once; one that keeps being
    // disconnected is rebound with a growing pause, so the system is never asked in a tight loop.
    private static final long STABLE_CONNECTION_MILLIS = 60_000;
    private static final long FIRST_REBIND_DELAY_MILLIS = 5_000;
    private static final long MAX_REBIND_DELAY_MILLIS = 5 * 60_000;
    private static final Object REBIND_TOKEN = new Object();
    /** Created on first use, so the timing rule above stays testable without an Android runtime. */
    private static final class MainThread {
        static final Handler handler = new Handler(Looper.getMainLooper());
    }
    private static long connectedAt;
    private static long rebindDelay;

    @Override public void onListenerConnected() {
        connected = true;
        connectedAt = SystemClock.elapsedRealtime();
        MainThread.handler.removeCallbacksAndMessages(REBIND_TOKEN);
        Context context = getApplicationContext();
        worker.execute(() -> Events.recover(context));
    }

    @Override public void onListenerDisconnected() {
        connected = false;
        rebindDelay = nextRebindDelay(SystemClock.elapsedRealtime() - connectedAt, rebindDelay);
        Context context = getApplicationContext();
        MainThread.handler.removeCallbacksAndMessages(REBIND_TOKEN);
        MainThread.handler.postDelayed(() -> rebind(context), REBIND_TOKEN, rebindDelay);
    }

    /**
     * Pause before asking Android to bind the listener again.
     *
     * @param connectedFor how long the connection that just ended had lasted
     * @param previous     the pause used before that connection
     */
    static long nextRebindDelay(long connectedFor, long previous) {
        if (connectedFor >= STABLE_CONNECTION_MILLIS) return 0;
        if (previous <= 0) return FIRST_REBIND_DELAY_MILLIS;
        return Math.min(previous * 2, MAX_REBIND_DELAY_MILLIS);
    }

    private static void rebind(Context context) {
        if (connected) return;
        try {
            requestRebind(new ComponentName(context, RelayListener.class));
        } catch (Exception ignored) {
            // Android rebinds on its own after the next boot or permission change.
        }
    }

    @Override public void onDestroy() {
        connected = false;
        super.onDestroy();
    }

    @Override public void onNotificationPosted(StatusBarNotification posted) {
        // Own notifications are never forwarded; nothing else is decided on the main thread.
        if (posted == null || getPackageName().equals(posted.getPackageName())) return;
        Context context = getApplicationContext();
        worker.execute(() -> {
            try {
                handle(context, posted);
            } catch (Exception failure) {
                Events.fault(context, "Ошибка обработки уведомления");
            }
        });
    }

    @Override public void onNotificationRemoved(StatusBarNotification removed) {
        if (removed == null) return;
        Context context = getApplicationContext();
        String notificationKey = removed.getKey();
        // Same thread as posting, so "posted, then removed" is never processed in the wrong order.
        worker.execute(() -> {
            try {
                QueueDb.get(context).forget(TextTools.hash(notificationKey));
            } catch (Exception ignored) {
                // A stale de-duplication record is harmless and expires with the table limit.
            }
        });
    }

    private static void handle(Context context, StatusBarNotification posted) throws Exception {
        Config config = Config.load(context);
        String pkg = posted.getPackageName();
        if (!config.enabled || !config.pushes || !config.apps.contains(pkg)) return;
        Notification notification = posted.getNotification();
        // A group summary only repeats its children.
        if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        if (posted.isOngoing() && !config.ongoing) return;

        Bundle extras = notification.extras;
        String title = string(extras.getCharSequence(Notification.EXTRA_TITLE));
        String text = text(extras);
        if (title.isEmpty() && text.isEmpty()) {
            Events.fault(context, "Выбранное приложение опубликовало уведомление без доступного текста; Android может скрывать содержимое.");
            return;
        }

        // Applications re-post unchanged notifications; the same content under the same key is one event.
        QueueDb db = QueueDb.get(context);
        String fingerprint = TextTools.hash(title + "\n" + text);
        String key = TextTools.hash(posted.getKey());
        if (db.seen(key, fingerprint)) return;

        String label = appLabel(context, pkg);
        Events.Incoming event = new Events.Incoming(
                TextTools.hash(key + "|" + posted.getPostTime() + "|" + fingerprint), Events.KIND_NOTIFICATION);
        event.title = title;
        event.message = text;
        event.data = label + " (" + pkg + ")\n" + title + "\n" + text;
        event.app = label;
        event.pkg = pkg;
        // Not queued (for example forwarding has no recipient yet): let a later re-post try again.
        if (!Events.capture(context, event)) db.forget(key);
    }

    /** The most complete text available: expanded text, then short text, then lines, then the last message. */
    @SuppressWarnings("deprecation") // getParcelableArray(String) is what MessagingStyle bundles are read with.
    private static String text(Bundle extras) {
        String text = string(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (!text.isEmpty()) return text;
        text = string(extras.getCharSequence(Notification.EXTRA_TEXT));
        if (!text.isEmpty()) return text;

        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            StringBuilder joined = new StringBuilder();
            for (CharSequence line : lines) joined.append(line).append('\n');
            text = joined.toString().trim();
            if (!text.isEmpty()) return text;
        }

        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages != null) {
            List<Notification.MessagingStyle.Message> parsed =
                    Notification.MessagingStyle.Message.getMessagesFromBundleArray(messages);
            if (!parsed.isEmpty()) return string(parsed.get(parsed.size() - 1).getText());
        }
        return "";
    }

    private static String appLabel(Context context, String pkg) {
        try {
            PackageManager packages = context.getPackageManager();
            return packages.getApplicationLabel(packages.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception unavailable) {
            return pkg;
        }
    }

    private static String string(CharSequence value) {
        return value == null ? "" : value.toString();
    }
}
