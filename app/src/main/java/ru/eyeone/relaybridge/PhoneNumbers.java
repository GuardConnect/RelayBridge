package ru.eyeone.relaybridge;

import android.content.Context;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import java.util.List;
import java.util.Locale;

/** Normalize only valid phone numbers; never invent a country from the UI locale. */
final class PhoneNumbers {
    private PhoneNumbers() { }

    /** E.164 form of {@code raw} using the country of the SIM that received the event. */
    static String international(Context context, String raw, int subscription) {
        String original = ContactNames.number(raw);
        if (!numeric(original)) return original;
        return format(original, country(context, subscription));
    }

    /** E.164 form of {@code raw} for a two-letter country, or the original text when it cannot be determined. */
    static String format(String raw, String country) {
        String original = ContactNames.number(raw);
        if (!numeric(original)) return original;
        String region = iso(country);
        // A national number without a known country stays as received.
        if (region.isEmpty() && !original.trim().startsWith("+")) return original;
        String formatted = PhoneNumberUtils.formatNumberToE164(original, region);
        return formatted == null ? original : formatted;
    }

    /** Do not turn sender names, USSD, extensions or short codes into phone numbers. */
    private static boolean numeric(String value) {
        return value.matches("\\+?[0-9 ()\\-./]+") && value.replaceAll("[^0-9]", "").length() >= 7;
    }

    /** Upper-case two-letter country code, or an empty string. */
    static String iso(String value) {
        return value != null && value.matches("[a-zA-Z]{2}") ? value.toUpperCase(Locale.ROOT) : "";
    }

    /** Country of a subscription: the current network first, then the SIM itself. */
    static String country(Context context, int subscription) {
        try {
            if (subscription < 0) {
                List<SimCards.Card> cards = SimCards.active(context);
                // A default SIM could be a different country's SIM on a dual-SIM phone.
                if (cards.size() != 1) return "";
                subscription = cards.get(0).id;
            }
            TelephonyManager base = context.getSystemService(TelephonyManager.class);
            if (base == null) return "";
            TelephonyManager manager = base.createForSubscriptionId(subscription);
            String network = "";
            try {
                network = iso(manager.getNetworkCountryIso());
            } catch (SecurityException | UnsupportedOperationException ignored) {
                // Fall back to the SIM country below.
            }
            if (!network.isEmpty()) return network;
            try {
                return iso(manager.getSimCountryIso());
            } catch (SecurityException | UnsupportedOperationException ignored) {
                return "";
            }
        } catch (RuntimeException unavailable) {
            return "";
        }
    }
}
