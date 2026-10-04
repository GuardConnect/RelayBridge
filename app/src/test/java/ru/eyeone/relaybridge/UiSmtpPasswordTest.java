package ru.eyeone.relaybridge;
import android.widget.EditText;
import android.widget.Spinner;
import java.lang.reflect.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=35)
public class UiSmtpPasswordTest {
 @Test public void gmailPasswordFromFieldReachesAuthWithoutDisplaySpaces() throws Exception {
  try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity activity=controller.get();
   edit(activity,"host","smtp.gmail.com");edit(activity,"user","fixture.sender@gmail.com");
   edit(activity,"from","fixture.sender@gmail.com");edit(activity,"to","fixture.recipient@example.com");
   String entered=" abcd efgh ijkl mnop ";edit(activity,"password",entered);
   for(int tls=0;tls<2;tls++)for(int auth=0;auth<3;auth++){
    ((Spinner)field(activity,"tls")).setSelection(tls);((Spinner)field(activity,"auth")).setSelection(auth);
    edit(activity,"port",tls==0?"587":"465");
    Config c=read(activity);c.validateEmail();assertEquals(entered,c.password);
    assertEquals("abcdefghijklmnop",SmtpClient.passwordForAuth(c));
   }
  }
 }
 @Test public void smtpTestReadsEditedPasswordRatherThanSavedConfig() throws Exception {
  try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity activity=controller.get();((Config)field(activity,"cfg")).password="old-fixture-password";
   edit(activity,"password","first-fixture-password");Config first=read(activity);
   edit(activity,"password","second-fixture-password");Config second=read(activity);
   assertEquals("first-fixture-password",first.password);assertEquals("second-fixture-password",second.password);
  }
 }
 private static Config read(MainActivity a)throws Exception{Method m=MainActivity.class.getDeclaredMethod("readForm",boolean.class);m.setAccessible(true);return (Config)m.invoke(a,true);}
 private static Object field(Object a,String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}
 private static void edit(MainActivity a,String name,String text)throws Exception{((EditText)field(a,name)).setText(text);}
}
