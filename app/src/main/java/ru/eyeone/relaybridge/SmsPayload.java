package ru.eyeone.relaybridge;

import android.content.Intent;
import android.telephony.SmsMessage;
import android.util.Base64;

/** Decode a complete SMS broadcast without discarding messages for missing OEM metadata. */
final class SmsPayload {
    private static final String GSM = "3gpp", CDMA = "3gpp2";

    final SmsMessage[] parts;
    /** All raw PDUs; two different messages never share it, a re-delivered broadcast always does. */
    private final String identity;

    private SmsPayload(SmsMessage[] parts, String identity) {
        this.parts = parts;
        this.identity = identity;
    }

    /** @throws IllegalArgumentException with a user-readable reason when any part cannot be read */
    @SuppressWarnings("deprecation") // The typed getter rejects the Object[] of byte[] sent by telephony.
    static SmsPayload decode(Intent intent) {
        Object raw = intent.getSerializableExtra("pdus");
        if (!(raw instanceof Object[]) || ((Object[]) raw).length == 0)
            throw new IllegalArgumentException("Android передал SMS без данных сообщения");
        Object[] pdus = (Object[]) raw;

        String declared = intent.getStringExtra("format");
        boolean formatKnown = declared != null && !declared.isBlank();
        if (formatKnown && !declared.equals(GSM) && !declared.equals(CDMA))
            throw new IllegalArgumentException("Android передал неизвестный формат SMS");

        SmsMessage[] parts = new SmsMessage[pdus.length];
        StringBuilder identity = new StringBuilder();
        for (int index = 0; index < pdus.length; index++) {
            if (!(pdus[index] instanceof byte[]) || ((byte[]) pdus[index]).length == 0)
                throw new IllegalArgumentException("Android передал повреждённую часть SMS");
            byte[] pdu = (byte[]) pdus[index];
            // Without a declared format try GSM first, then CDMA.
            SmsMessage part = parse(pdu, formatKnown ? declared : GSM);
            if (part == null && !formatKnown) part = parse(pdu, CDMA);
            // A multipart SMS is forwarded whole or not at all.
            if (part == null || part.getMessageBody() == null)
                throw new IllegalArgumentException(
                        "Не удалось прочитать SMS целиком: часть " + (index + 1) + " из " + pdus.length);
            parts[index] = part;
            identity.append(Base64.encodeToString(pdu, Base64.NO_WRAP)).append('|');
        }
        return new SmsPayload(parts, identity.toString());
    }

    private static SmsMessage parse(byte[] pdu, String format) {
        try {
            return SmsMessage.createFromPdu(pdu, format);
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    /** De-duplication key of this broadcast on a given subscription. */
    String key(int subscription) {
        return TextTools.hash("sms-pdu|" + subscription + '|' + identity);
    }

    String body() {
        StringBuilder text = new StringBuilder();
        for (SmsMessage part : parts) text.append(part.getMessageBody());
        return text.toString();
    }
}
