package ru.eyeone.relaybridge;
import android.Manifest;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.provider.ContactsContract;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class ContactNamesTest {
 private android.app.Application app;private TestProvider provider;
 @Before public void setup(){app=RuntimeEnvironment.getApplication();Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS);provider=Robolectric.buildContentProvider(TestProvider.class).create().get();ShadowContentResolver.registerProviderInternal("com.android.contacts",provider);}
 @Test public void knownContactIsLookedUpByFullEncodedNumber(){provider.names=List.of("  Алексей\nПетров  ");String number="+7 (900) 123-45-67";assertEquals("Алексей Петров",ContactNames.lookup(app,number));assertEquals(number,provider.uri.getLastPathSegment());assertArrayEquals(new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},provider.projection);assertTrue(provider.cursor.isClosed());}
 @Test public void absentContactIsUnknown(){assertEquals(ContactNames.UNKNOWN,ContactNames.lookup(app,"+79001234567"));assertEquals(1,provider.queries);}
 @Test public void deniedPermissionDoesNotReadProvider(){Shadows.shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS);assertEquals(ContactNames.UNKNOWN,ContactNames.lookup(app,"+79001234567"));assertEquals(0,provider.queries);}
 @Test public void ambiguousNamesNeverIdentifyWrongPerson(){provider.names=List.of("Алексей","Мария");assertEquals(ContactNames.UNKNOWN,ContactNames.lookup(app,"+79001234567"));provider.names=List.of("Алексей","Алексей");assertEquals("Алексей",ContactNames.lookup(app,"+79001234567"));}
 @Test public void providerFailureDoesNotPreventNumberOnlyMessage(){provider.fail=true;assertEquals(ContactNames.UNKNOWN,ContactNames.lookup(app,"+79001234567"));assertTrue(ContactNames.smsData(ContactNames.UNKNOWN,"+79001234567","Sim 1","Текст").contains("Номер: +79001234567"));}
 @Test public void hiddenAndAlphanumericSendersDoNotQueryPhoneContacts(){for(String number:new String[]{null,"","BANK","Скрыт или недоступен"})assertEquals(ContactNames.UNKNOWN,ContactNames.lookup(app,number));assertEquals(0,provider.queries);assertEquals("BANK",ContactNames.number("BANK"));assertEquals("Скрыт или недоступен",ContactNames.number(null));}
 @Test public void smsCallAndCustomTemplateKeepSenderSeparateFromOriginalNumber(){String number="+7 (900) 123-45-67";String data=ContactNames.smsData(ContactNames.UNKNOWN,number,"Sim 1","Текст");assertEquals("От: Неизвестный отправитель\nНомер: "+number+"\nSIM: Sim 1\nТекст",data);assertEquals("От: Мария\nSIM: Sim 2\nНомер: "+number,ContactNames.callData("Мария",number,"Sim 2"));Map<String,String> values=MessageTemplate.values("SMS","Заголовок","Текст",data,number,"Sim 1","","","Мария",0);assertEquals("Мария\n"+number,MessageTemplate.render("{{sender}}\n{{number}}",values));}
 public static class TestProvider extends ContentProvider {
  List<String> names=Collections.emptyList();int queries;boolean fail;Uri uri;String[] projection;MatrixCursor cursor;
  public boolean onCreate(){return true;}
  public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){queries++;this.uri=uri;this.projection=projection;if(fail)throw new SecurityException("Permission revoked");cursor=new MatrixCursor(new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME});for(String name:names)cursor.addRow(new Object[]{name});return cursor;}
  public String getType(Uri uri){return null;}public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
 }
}
