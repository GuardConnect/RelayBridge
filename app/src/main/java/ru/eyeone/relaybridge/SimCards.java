package ru.eyeone.relaybridge;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Active SIM cards and the mapping of incoming events to them. A SIM is never guessed. */
final class SimCards {
    private static final int UNKNOWN = -1;

    // Extras seen in SMS and call broadcasts of different Android versions and vendors.
    private static final String[] SMS_SUBSCRIPTION_KEYS =
            {SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, "subscription", TelephonyManager.EXTRA_SUBSCRIPTION_ID};
    private static final String[] SMS_SLOT_KEYS = {SubscriptionManager.EXTRA_SLOT_INDEX, "slot", "phone"};
    private static final String[] CALL_SUBSCRIPTION_KEYS =
            {TelephonyManager.EXTRA_SUBSCRIPTION_ID, SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, "subscription"};

    private SimCards() { }

    static final class Card {
        final int id, slot;
        final String label;

        Card(int id, int slot, CharSequence carrier) {
            this.id = id;
            this.slot = slot;
            this.label = SimRules.label(slot, carrier);
        }
    }

    static boolean permitted(Context context) {
        return context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    /** Active cards ordered by slot; empty without the phone permission or telephony. */
    static List<Card> active(Context context) {
        // Checked inline (not through permitted()) so that lint can see the permission guard.
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            return Collections.emptyList();
        List<Card> cards = new ArrayList<>();
        try {
            SubscriptionManager manager = context.getSystemService(SubscriptionManager.class);
            if (manager == null) return cards;
            List<SubscriptionInfo> infos = manager.getActiveSubscriptionInfoList();
            if (infos != null) {
                for (SubscriptionInfo info : infos)
                    cards.add(new Card(info.getSubscriptionId(), info.getSimSlotIndex(), info.getCarrierName()));
            }
        } catch (SecurityException | UnsupportedOperationException unavailable) {
            // Keep whatever was collected; the caller treats missing cards as "unknown SIM".
        }
        cards.sort(Comparator
                .comparingInt((Card card) -> card.slot < 0 ? Integer.MAX_VALUE : card.slot)
                .thenComparingInt(card -> card.id));
        return cards;
    }

    /**
     * Subscription that received an SMS broadcast, or -1.
     * Some modem/OEM SMS broadcasts use a Long or supply only a zero-based slot.
     */
    static int smsSubscription(Context context, Intent intent) {
        for (String key : SMS_SUBSCRIPTION_KEYS) {
            int id = numberExtra(intent, key);
            if (id >= 0) return id;
        }
        List<Card> cards = active(context);
        for (String key : SMS_SLOT_KEYS) {
            int slot = numberExtra(intent, key);
            if (slot < 0) continue;
            // An explicit but unmapped or ambiguous slot must not select a different SIM.
            return cardInSlot(cards, slot);
        }
        return cards.size() == 1 ? cards.get(0).id : UNKNOWN;
    }

    private static int cardInSlot(List<Card> cards, int slot) {
        int found = UNKNOWN;
        for (Card card : cards) {
            if (card.slot != slot) continue;
            if (found >= 0) return UNKNOWN;
            found = card.id;
        }
        return found;
    }

    /** A non-negative Integer or Long extra that fits into an int, otherwise -1. */
    @SuppressWarnings("deprecation") // Typed getters cannot read "Integer or Long" in one call.
    private static int numberExtra(Intent intent, String key) {
        Bundle extras = intent.getExtras();
        Object value = extras == null ? null : extras.get(key);
        if (!(value instanceof Integer) && !(value instanceof Long)) return UNKNOWN;
        long number = ((Number) value).longValue();
        return number >= 0 && number <= Integer.MAX_VALUE ? (int) number : UNKNOWN;
    }

    /**
     * Subscription of a phone-state broadcast, or -1.
     * PHONE_STATE does not guarantee subscription extras on every device,
     * so for a ringing call the single ringing card is used when it is unambiguous.
     */
    static int callSubscription(Context context, Intent intent) {
        List<Card> cards = active(context);
        for (String key : CALL_SUBSCRIPTION_KEYS) {
            int id = intent.getIntExtra(key, UNKNOWN);
            if (id < 0) continue;
            for (Card card : cards) {
                if (card.id == id) return id;
            }
        }
        if (!TelephonyManager.EXTRA_STATE_RINGING.equals(intent.getStringExtra(TelephonyManager.EXTRA_STATE)))
            return UNKNOWN;
        TelephonyManager manager = context.getSystemService(TelephonyManager.class);
        if (manager == null) return UNKNOWN;
        int[] ringing = new int[cards.size()];
        int count = 0;
        try {
            for (Card card : cards) {
                if (manager.createForSubscriptionId(card.id).getCallStateForSubscription()
                        == TelephonyManager.CALL_STATE_RINGING) ringing[count++] = card.id;
            }
        } catch (SecurityException | UnsupportedOperationException unavailable) {
            return UNKNOWN;
        }
        return SimRules.unique(ringing, count);
    }

    static String label(Context context, int subscription) {
        for (Card card : active(context)) {
            if (card.id == subscription) return card.label;
        }
        return SimRules.label(-1, null);
    }
}
