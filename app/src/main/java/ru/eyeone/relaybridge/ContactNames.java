package ru.eyeone.relaybridge;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Local, permission-gated lookup for the current event; no address-book export. */
final class ContactNames {
    static final String UNKNOWN = "Неизвестный отправитель";

    private static final String HIDDEN_NUMBER = "Скрыт или недоступен";
    private static final int LOOKUP_TIMEOUT_SECONDS = 2;
    private static final int MAX_NAME = 200;

    private static final ScheduledExecutorService TIMEOUTS = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "relay-contact-timeout");
        thread.setDaemon(true);
        return thread;
    });

    private ContactNames() { }

    /** The number as received, or a fixed text for a hidden one. */
    static String number(String raw) {
        return raw == null || raw.isBlank() ? HIDDEN_NUMBER : raw;
    }

    /**
     * Contact name for a phone number. Returns {@link #UNKNOWN} without the contacts permission,
     * for non-phone senders, on any provider failure and when different contacts share the number.
     */
    static String lookup(Context context, String number) {
        if (number == null || !PhoneNumberUtils.isGlobalPhoneNumber(PhoneNumberUtils.stripSeparators(number)))
            return UNKNOWN;
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            return UNKNOWN;

        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        String[] projection = {ContactsContract.PhoneLookup.DISPLAY_NAME};
        CancellationSignal signal = new CancellationSignal();
        // A slow contacts provider must not hold up the capture of the event itself.
        ScheduledFuture<?> expiry = TIMEOUTS.schedule(signal::cancel, LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null, signal)) {
            if (cursor == null) return UNKNOWN;
            int column = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
            if (column < 0) return UNKNOWN;
            Set<String> names = new HashSet<>();
            while (cursor.moveToNext()) {
                signal.throwIfCanceled();
                String name = clean(cursor.getString(column));
                if (!name.isEmpty()) names.add(name);
                if (names.size() > 1) return UNKNOWN;
            }
            return names.size() == 1 ? names.iterator().next() : UNKNOWN;
        } catch (RuntimeException unavailable) {
            return UNKNOWN;
        } finally {
            expiry.cancel(false);
        }
    }

    /** One bounded line: a contact name can never add lines to a message. */
    private static String clean(String raw) {
        if (raw == null) return "";
        String name = raw.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        return name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
    }

    static String smsData(String sender, String number, String sim, String message) {
        return "От: " + sender + "\nНомер: " + number(number) + "\nSIM: " + sim + "\n" + message;
    }

    static String callData(String sender, String number, String sim) {
        return "От: " + sender + "\n" + SimRules.callData(sim, number(number));
    }
}
