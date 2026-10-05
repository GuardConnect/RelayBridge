package ru.eyeone.relaybridge;

import android.content.Context;
import android.text.format.DateFormat;
import java.util.ArrayDeque;

/**
 * Bounded diagnostic log of SMTP sessions: stages and numeric replies only.
 * Kept in memory and saved encrypted so it survives a restart of the process.
 */
final class SmtpLog {
    private static final String KEY = "smtp-log";
    private static final int MAX_LINES = 200;
    private static final String EMPTY = "Лог пуст. Запустите тест SMTP во вкладке Отправка.\n\n"
            + "Лог содержит этапы и числовые ответы. AUTH-данные, адреса и письмо скрыты.";

    private static final ArrayDeque<String> lines = new ArrayDeque<>();
    private static Context context;
    private static long sequence;

    private SmtpLog() { }

    /** Restore the saved log. Called once when the process starts. */
    static synchronized void init(Context anyContext) {
        context = anyContext.getApplicationContext();
        try {
            String sealed = Config.prefs(context).getString(KEY, "");
            if (sealed.isEmpty()) return;
            for (String line : Crypto.open(sealed).split("\n")) append(line);
        } catch (Exception unreadable) {
            // An unreadable diagnostic log is simply started anew.
        }
    }

    /** Start a new session and return its number for {@link #line}. */
    static synchronized long begin() {
        long id = ++sequence;
        line(id, "Новая SMTP-сессия");
        return id;
    }

    static synchronized void line(long id, String value) {
        append(DateFormat.format("HH:mm:ss", System.currentTimeMillis()) + " #" + id + " " + value);
    }

    private static void append(String value) {
        lines.addLast(value);
        while (lines.size() > MAX_LINES) lines.removeFirst();
    }

    static synchronized String snapshot() {
        return lines.isEmpty() ? EMPTY : String.join("\n", lines);
    }

    static synchronized void persist() {
        if (context == null) return;
        try {
            Config.prefs(context).edit().putString(KEY, Crypto.seal(String.join("\n", lines))).apply();
        } catch (Exception ignored) {
            // The log stays available in memory.
        }
    }

    static synchronized void clear() {
        lines.clear();
        if (context != null) Config.prefs(context).edit().remove(KEY).apply();
    }
}
