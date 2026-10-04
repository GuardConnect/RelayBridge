package ru.eyeone.relaybridge;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import java.util.*;
import java.util.concurrent.*;
/** Local, permission-gated lookup for the current event; no address-book export. */
final class ContactNames {
    static final String UNKNOWN="Неизвестный отправитель";
    private static final ScheduledExecutorService timeout=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"relay-contact-timeout");t.setDaemon(true);return t;});
    static String number(String raw){return raw==null||raw.isBlank()?"Скрыт или недоступен":raw;}
    static String lookup(Context context,String number){
        if(number==null||!PhoneNumberUtils.isGlobalPhoneNumber(PhoneNumberUtils.stripSeparators(number))||context.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)return UNKNOWN;
        CancellationSignal signal=new CancellationSignal();ScheduledFuture<?> expiry=timeout.schedule(signal::cancel,2,TimeUnit.SECONDS);
        try(Cursor cursor=context.getContentResolver().query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(number)),new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},null,null,null,signal)){
            if(cursor==null)return UNKNOWN;int column=cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);if(column<0)return UNKNOWN;
            Set<String> names=new HashSet<>();while(cursor.moveToNext()){
                signal.throwIfCanceled();String raw=cursor.getString(column);if(raw==null)continue;String name=raw.replaceAll("[\\p{Cntrl}\\s]+"," ").trim();if(!name.isEmpty())names.add(name.length()>200?name.substring(0,200):name);if(names.size()>1)return UNKNOWN;
            }
            return names.size()==1?names.iterator().next():UNKNOWN;
        }catch(RuntimeException unavailable){return UNKNOWN;}finally{expiry.cancel(false);}
    }
    static String smsData(String sender,String number,String sim,String message){return "От: "+sender+"\nНомер: "+number(number)+"\nSIM: "+sim+"\n"+message;}
    static String callData(String sender,String number,String sim){return "От: "+sender+"\n"+SimRules.callData(sim,number(number));}
}
