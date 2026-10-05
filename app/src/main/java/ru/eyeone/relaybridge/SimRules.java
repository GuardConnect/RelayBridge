package ru.eyeone.relaybridge;

import java.util.Set;

/** Subscription IDs select cards; slot indices are used only for display. */
final class SimRules {
    private SimRules() { }

    /** "All SIMs" accepts everything, including an unknown card; otherwise the ID must be selected. */
    static boolean accepts(boolean all, Set<Integer> selected, int subscription) {
        if (all) return true;
        return subscription >= 0 && selected.contains(subscription);
    }

    /** The only candidate, or -1 when there is none or more than one. */
    static int unique(int[] ids, int count) {
        return count == 1 ? ids[0] : -1;
    }

    static String callData(String sim, String number) {
        return "SIM: " + sim + "\nНомер: " + number;
    }

    /** One-line label such as "Sim 2 Megafon"; carrier text can never add lines. */
    static String label(int slot, CharSequence carrier) {
        String name = carrier == null ? "" : carrier.toString().replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        if (name.equalsIgnoreCase("МТС") || name.equalsIgnoreCase("MTS")) name = "MTS";
        if (name.equalsIgnoreCase("МегаФон") || name.equalsIgnoreCase("MegaFon")) name = "Megafon";
        if (name.isEmpty()) name = "Оператор не определён";
        String position = slot >= 0 ? String.valueOf(slot + 1) : "?";
        return "Sim " + position + " " + name;
    }
}
