package ru.eyeone.relaybridge;
import org.junit.Test;
import java.util.Set;
import android.Manifest;
import static org.junit.Assert.*;
public class PermissionRequestsTest {
 @Test public void requestCodesAreDistinct(){assertEquals(6,Set.of(PermissionRequests.SMS,PermissionRequests.PHONE,PermissionRequests.CALL_LOG,PermissionRequests.STATUS,PermissionRequests.SIM_PHONE,PermissionRequests.CALL_SIM_PHONE).size());}
 @Test public void notificationGrantNeverOpensSimSelector(){assertTrue(PermissionRequests.granted(PermissionRequests.STATUS,new String[]{Manifest.permission.POST_NOTIFICATIONS},new int[]{0}));assertFalse(PermissionRequests.opensSims(PermissionRequests.STATUS,new String[]{Manifest.permission.POST_NOTIFICATIONS},new int[]{0}));}
 @Test public void ordinaryPhoneGrantDoesNotContinueToSims(){assertFalse(PermissionRequests.opensSims(PermissionRequests.PHONE,new String[]{Manifest.permission.READ_PHONE_STATE},new int[]{0}));}
 @Test public void simPhoneGrantContinuesToSelector(){assertTrue(PermissionRequests.opensSims(PermissionRequests.SIM_PHONE,new String[]{Manifest.permission.READ_PHONE_STATE},new int[]{0}));}
 @Test public void wrongPermissionCannotContinueEvenWithSimCode(){assertFalse(PermissionRequests.opensSims(PermissionRequests.SIM_PHONE,new String[]{Manifest.permission.POST_NOTIFICATIONS},new int[]{0}));}
 @Test public void cancellationDenialAndMalformedResultsDoNotContinue(){assertFalse(PermissionRequests.opensSims(PermissionRequests.SIM_PHONE,new String[]{},new int[]{}));assertFalse(PermissionRequests.opensSims(PermissionRequests.SIM_PHONE,new String[]{Manifest.permission.READ_PHONE_STATE},new int[]{-1}));assertFalse(PermissionRequests.opensSims(PermissionRequests.SIM_PHONE,null,null));assertFalse(PermissionRequests.granted(PermissionRequests.STATUS,new String[]{Manifest.permission.POST_NOTIFICATIONS},new int[]{}));}
 @Test public void callSimPermissionIsIndependentOfSmsAndNotifications(){assertEquals(Manifest.permission.READ_PHONE_STATE,PermissionRequests.permission(PermissionRequests.CALL_SIM_PHONE));assertTrue(PermissionRequests.granted(PermissionRequests.CALL_SIM_PHONE,new String[]{Manifest.permission.READ_PHONE_STATE},new int[]{0}));assertFalse(PermissionRequests.opensSims(PermissionRequests.CALL_SIM_PHONE,new String[]{Manifest.permission.READ_PHONE_STATE},new int[]{0}));}
 @Test public void unknownRequestsCannotGrantOrContinue(){assertFalse(PermissionRequests.granted(12,new String[]{Manifest.permission.POST_NOTIFICATIONS},new int[]{0}));assertFalse(PermissionRequests.opensSims(12,new String[]{Manifest.permission.READ_PHONE_STATE},new int[]{0}));}
}
