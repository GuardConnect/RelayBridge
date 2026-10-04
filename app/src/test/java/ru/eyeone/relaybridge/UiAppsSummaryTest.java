package ru.eyeone.relaybridge;
import android.content.pm.ApplicationInfo;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class UiAppsSummaryTest {
 @Test public void countIsSeparateFromChooserCaption() throws Exception {
  try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity a=controller.get();assertEquals("Выберите приложения из списка установленных",((Button)field(a,"appsButton")).getText().toString());assertEquals("Выбрано приложений: 0",((TextView)field(a,"selectedAppsCount")).getText().toString());
  }
 }
 @Test public void eachSelectedAppShowsNameAndPackageInOwnCard() throws Exception {
  try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity a=controller.get();render(a);LinearLayout list=(LinearLayout)field(a,"selectedAppsList");assertEquals(2,list.getChildCount());
   for(int i=0;i<2;i++){LinearLayout row=(LinearLayout)list.getChildAt(i);LinearLayout words=(LinearLayout)row.getChildAt(1);assertEquals(i==0?"Telegram":"Mail",((TextView)words.getChildAt(0)).getText().toString());assertEquals(i==0?"org.telegram.messenger":"com.example.mail",((TextView)words.getChildAt(1)).getText().toString());}
  }
 }
 static Object field(MainActivity a,String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}
 static void render(MainActivity a)throws Exception{
  Class<?> row=Class.forName("ru.eyeone.relaybridge.MainActivity$AppRow");Constructor<?> constructor=row.getDeclaredConstructor(ApplicationInfo.class,String.class);constructor.setAccessible(true);List<Object> apps=new ArrayList<>();
  for(String[] entry:new String[][]{{"org.telegram.messenger","Telegram"},{"com.example.mail","Mail"}}){ApplicationInfo info=new ApplicationInfo();info.packageName=entry[0];apps.add(constructor.newInstance(info,entry[1]));}
  Method render=MainActivity.class.getDeclaredMethod("renderSelectedApps",List.class);render.setAccessible(true);render.invoke(a,apps);
 }
}
