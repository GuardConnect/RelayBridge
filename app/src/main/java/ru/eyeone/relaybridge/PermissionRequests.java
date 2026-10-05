package ru.eyeone.relaybridge;

import android.Manifest;
import android.content.pm.PackageManager;

/** Request codes of runtime permissions and interpretation of their results. */
final class PermissionRequests {
    static final int SMS = 101;
    static final int PHONE = 102;
    static final int CALL_LOG = 103;
    static final int STATUS = 104;
    static final int SIM_PHONE = 105;
    static final int CALL_SIM_PHONE = 106;
    static final int CONTACTS = 107;

    private PermissionRequests() { }

    /** Permission asked by a request code, or null for an unknown code. */
    static String permission(int request) {
        switch (request) {
            case SMS:
                return Manifest.permission.RECEIVE_SMS;
            case PHONE:
            case SIM_PHONE:
            case CALL_SIM_PHONE:
                return Manifest.permission.READ_PHONE_STATE;
            case CALL_LOG:
                return Manifest.permission.READ_CALL_LOG;
            case CONTACTS:
                return Manifest.permission.READ_CONTACTS;
            case STATUS:
                return Manifest.permission.POST_NOTIFICATIONS;
            default:
                return null;
        }
    }

    /** True only for a well-formed result that grants exactly the expected permission. */
    static boolean granted(int request, String[] permissions, int[] results) {
        String expected = permission(request);
        if (expected == null || permissions == null || results == null) return false;
        if (permissions.length != 1 || results.length != 1) return false;
        return expected.equals(permissions[0]) && results[0] == PackageManager.PERMISSION_GRANTED;
    }

    /** Only the SMS SIM flow continues to the SIM selector after the grant. */
    static boolean opensSims(int request, String[] permissions, int[] results) {
        return request == SIM_PHONE && granted(request, permissions, results);
    }
}
