package ru.eyeone.relaybridge;
import android.Manifest;
final class PermissionRequests {
    static final int SMS=101,PHONE=102,CALL_LOG=103,STATUS=104,SIM_PHONE=105,CALL_SIM_PHONE=106;
    static String permission(int request){return switch(request){
        case SMS->Manifest.permission.RECEIVE_SMS;
        case PHONE,SIM_PHONE,CALL_SIM_PHONE->Manifest.permission.READ_PHONE_STATE;
        case CALL_LOG->Manifest.permission.READ_CALL_LOG;
        case STATUS->Manifest.permission.POST_NOTIFICATIONS;
        default->null;
    };}
    static boolean granted(int request,String[] permissions,int[] results){
        String expected=permission(request);
        return expected!=null&&permissions!=null&&results!=null&&permissions.length==1&&results.length==1&&expected.equals(permissions[0])&&results[0]==0;
    }
    static boolean opensSims(int request,String[] permissions,int[] results){return request==SIM_PHONE&&granted(request,permissions,results);}
}
