package ru.eyeone.relaybridge;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/**
 * Local outbox and history. Message text and delivery credentials are stored encrypted;
 * time, type and per-channel statuses are plain so the list can be shown without decrypting everything twice.
 */
final class QueueDb extends SQLiteOpenHelper {
    /** Per-channel delivery states stored in the {@code tg} and {@code mail} columns. */
    static final int PENDING = 0, DELIVERED = 1, DISABLED = 2, FAILED = 3;

    private static final String DB_NAME = "outbox.db";
    private static final int SCHEMA = 1;
    private static final long RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000;
    private static final int MAX_ROWS = 500;

    private static QueueDb instance;

    /** Incremented on every change so the history screen can skip needless reloads. */
    private volatile long revision;

    static synchronized QueueDb get(Context context) {
        if (instance == null) instance = new QueueDb(context.getApplicationContext());
        return instance;
    }

    private QueueDb(Context context) {
        super(context, DB_NAME, null, SCHEMA);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY AUTOINCREMENT, dedup TEXT UNIQUE, created INTEGER,"
                + " kind TEXT, payload TEXT, route TEXT, tg INTEGER, mail INTEGER, pos INTEGER DEFAULT 0,"
                + " error TEXT DEFAULT '')");
        db.execSQL("CREATE TABLE seen(k TEXT PRIMARY KEY, fingerprint TEXT, ts INTEGER)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Unsupported schema");
    }

    long revision() {
        return revision;
    }

    private static String[] arg(long value) {
        return new String[]{String.valueOf(value)};
    }

    private static long oldestKept() {
        return System.currentTimeMillis() - RETENTION_MILLIS;
    }

    // ---- Retention -------------------------------------------------------------------------

    /** Drop events older than the retention period. */
    synchronized void prune() {
        if (getWritableDatabase().delete("events", "created < ?", arg(oldestKept())) > 0) revision++;
    }

    synchronized void clear() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("events", null, null);
        db.delete("seen", null, null);
        revision++;
    }

    // ---- Queue -----------------------------------------------------------------------------

    /**
     * Store a rendered event with a snapshot of the current delivery credentials.
     *
     * @return the new row ID, or -1 when an event with the same {@code key} already exists
     */
    synchronized long add(String key, String kind, String body, Config config) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        db.delete("events", "created < ?", arg(now - RETENTION_MILLIS));
        ContentValues row = new ContentValues();
        row.put("dedup", key);
        row.put("created", now);
        row.put("kind", kind);
        row.put("payload", Crypto.seal(body));
        row.put("route", Crypto.seal(config.deliveryJson()));
        row.put("tg", config.telegram ? PENDING : DISABLED);
        row.put("mail", config.email ? PENDING : DISABLED);
        long id = db.insertWithOnConflict("events", null, row, SQLiteDatabase.CONFLICT_IGNORE);
        // Retention cap; oldest rows are dropped, preventing unbounded private-data storage.
        db.execSQL("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY id DESC LIMIT " + MAX_ROWS + ")");
        revision++;
        return id;
    }

    /** An event that still has a pending channel, or null when there is nothing left to deliver. */
    synchronized Event event(long id) throws Exception {
        String payload, route, kind;
        int tg, mail, pos;
        long created;
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT payload,tg,mail,pos,kind,route,created FROM events WHERE id=?", arg(id))) {
            if (!cursor.moveToFirst()) return null;
            payload = cursor.getString(0);
            tg = cursor.getInt(1);
            mail = cursor.getInt(2);
            pos = cursor.getInt(3);
            kind = cursor.getString(4);
            route = cursor.getString(5);
            created = cursor.getLong(6);
        }
        if (tg != PENDING && mail != PENDING) return null;
        if (created < oldestKept()) {
            deleteEvent(id);
            return null;
        }
        return new Event(Crypto.open(payload), tg, mail, pos, kind, Config.deliveryConfig(Crypto.open(route)));
    }

    synchronized boolean exists(long id) {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT 1 FROM events WHERE id=?", arg(id))) {
            return cursor.moveToFirst();
        }
    }

    /** IDs of unexpired events with at least one pending channel, oldest first. */
    synchronized long[] pending() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id FROM events WHERE (tg=0 OR mail=0) AND created>=? ORDER BY id", arg(oldestKept()))) {
            long[] ids = new long[cursor.getCount()];
            int count = 0;
            while (cursor.moveToNext()) ids[count++] = cursor.getLong(0);
            return ids;
        }
    }

    /** Set an integer column ({@code tg}, {@code mail} or {@code pos}) of one event. */
    synchronized void update(long id, String column, int value) {
        ContentValues row = new ContentValues();
        row.put(column, value);
        getWritableDatabase().update("events", row, "id=?", arg(id));
        revision++;
    }

    synchronized void error(long id, String message) {
        ContentValues row = new ContentValues();
        row.put("error", message);
        getWritableDatabase().update("events", row, "id=?", arg(id));
        revision++;
    }

    /** Forget the credential snapshot as soon as no channel is waiting for it. */
    synchronized void redactDone(long id) {
        getWritableDatabase().execSQL("UPDATE events SET route='' WHERE id=? AND tg<>0 AND mail<>0", new Object[]{id});
        revision++;
    }

    synchronized void deleteEvent(long id) {
        getWritableDatabase().delete("events", "id=?", arg(id));
        revision++;
    }

    /**
     * Put the failed channels of a finished event back into the queue with the current credentials.
     *
     * @return false when nothing can be retried
     */
    synchronized boolean retryFailed(long id, Config config) throws Exception {
        config.validate();
        if (!config.enabled) throw new IllegalArgumentException("Сначала включите пересылку");
        int tg, mail;
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT tg,mail,payload FROM events WHERE id=?", arg(id))) {
            if (!cursor.moveToFirst() || cursor.getString(2).isEmpty()) return false;
            tg = cursor.getInt(0);
            mail = cursor.getInt(1);
        }
        // Never replace credentials of an in-flight delivery or repeat a successful channel.
        if (tg == PENDING || mail == PENDING)
            throw new IllegalArgumentException("Дождитесь завершения текущей доставки");
        ContentValues row = new ContentValues();
        boolean retry = false;
        if (tg == FAILED && config.telegram) {
            row.put("tg", PENDING);
            row.put("pos", 0);
            retry = true;
        }
        if (mail == FAILED && config.email) {
            row.put("mail", PENDING);
            retry = true;
        }
        if (!retry) return false;
        row.put("route", Crypto.seal(config.deliveryJson()));
        row.put("error", "");
        getWritableDatabase().update("events", row, "id=?", arg(id));
        revision++;
        return true;
    }

    // ---- Notification de-duplication -------------------------------------------------------

    /**
     * Remember the content fingerprint of a notification.
     *
     * @return true when the same content was already recorded for this notification key
     */
    synchronized boolean seen(String key, String fingerprint) {
        SQLiteDatabase db = getWritableDatabase();
        try (Cursor cursor = db.query("seen", new String[]{"fingerprint"}, "k=?", new String[]{key},
                null, null, null)) {
            if (cursor.moveToFirst() && fingerprint.equals(cursor.getString(0))) return true;
        }
        ContentValues row = new ContentValues();
        row.put("k", key);
        row.put("fingerprint", fingerprint);
        row.put("ts", System.currentTimeMillis());
        db.insertWithOnConflict("seen", null, row, SQLiteDatabase.CONFLICT_REPLACE);
        db.execSQL("DELETE FROM seen WHERE k NOT IN (SELECT k FROM seen ORDER BY ts DESC LIMIT " + MAX_ROWS + ")");
        return false;
    }

    synchronized void forget(String key) {
        getWritableDatabase().delete("seen", "k=?", new String[]{key});
    }

    // ---- History ---------------------------------------------------------------------------

    /** Newest events first, with decrypted text. */
    synchronized List<HistoryRow> history() {
        prune();
        List<HistoryRow> rows = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id,created,kind,payload,tg,mail,error FROM events ORDER BY id DESC LIMIT " + MAX_ROWS, null)) {
            while (cursor.moveToNext()) {
                rows.add(new HistoryRow(cursor.getLong(0), cursor.getLong(1), cursor.getString(2),
                        readableBody(cursor.getString(3)), cursor.getInt(4), cursor.getInt(5), cursor.getString(6)));
            }
        }
        return rows;
    }

    private static String readableBody(String payload) {
        if (payload.isEmpty()) return "Содержимое удалено предыдущей версией";
        try {
            return Crypto.open(payload);
        } catch (Exception unreadable) {
            return "Содержимое недоступно";
        }
    }

    static String label(int state) {
        switch (state) {
            case PENDING:
                return "в очереди";
            case DELIVERED:
                return "доставлено";
            case DISABLED:
                return "выключено";
            default:
                return "ошибка";
        }
    }

    /** A queued event as needed by the delivery worker. */
    static final class Event {
        final String body, kind;
        final int tg, mail, pos;
        final Config route;

        Event(String body, int tg, int mail, int pos, String kind, Config route) {
            this.body = body;
            this.tg = tg;
            this.mail = mail;
            this.pos = pos;
            this.kind = kind;
            this.route = route;
        }
    }

    /** One line of the history screen. */
    static final class HistoryRow {
        final long id, created;
        final String kind, body, error;
        final int tg, mail;

        HistoryRow(long id, long created, String kind, String body, int tg, int mail, String error) {
            this.id = id;
            this.created = created;
            this.kind = kind;
            this.body = body;
            this.tg = tg;
            this.mail = mail;
            this.error = error;
        }
    }
}
