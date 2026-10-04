package ru.eyeone.relaybridge;
import android.view.*;
import android.widget.*;
import android.content.res.Configuration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class UiGridTest {
 @Test public void narrowScreenKeepsLabelsSeparateFromSwitches() throws Exception {check(320,1f);}
 @Test public void referenceWidthCentersNavigation() throws Exception {check(390,1f);}
 @Test public void largeFontDoesNotOverlapSwitches() throws Exception {check(320,1.3f);}
 @Test public void unconfirmedActivationKeepsSwitchOff() throws Exception {
  try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity activity=controller.get();java.lang.reflect.Field field=MainActivity.class.getDeclaredField("relayToggle");field.setAccessible(true);Switch toggle=(Switch)field.get(activity);
   assertFalse(toggle.isChecked());toggle.setChecked(true);assertFalse("Unconfirmed activation must stay off",toggle.isChecked());assertFalse(ru.eyeone.relaybridge.Config.load(activity).enabled);
  }
 }
 private void check(int width,float font) throws Exception {
  try(var controller=Robolectric.buildActivity(MainActivity.class)){
   MainActivity activity=controller.get();Configuration config=new Configuration(activity.getResources().getConfiguration());config.fontScale=font;activity.getResources().updateConfiguration(config,activity.getResources().getDisplayMetrics());controller.setup();
   java.lang.reflect.Method select=MainActivity.class.getDeclaredMethod("selectTab",int.class);select.setAccessible(true);
   View root=activity.findViewById(android.R.id.content);float density=activity.getResources().getDisplayMetrics().density;int pixels=Math.round(width*density);
   for(int tab=0;tab<4;tab++){select.invoke(activity,tab);root.measure(View.MeasureSpec.makeMeasureSpec(pixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(Math.round(844*density),View.MeasureSpec.EXACTLY));root.layout(0,0,pixels,Math.round(844*density));verify(root);if(tab==2){java.lang.reflect.Method access=MainActivity.class.getDeclaredMethod("updateAccess",boolean[].class);access.setAccessible(true);access.invoke(activity,(Object)new boolean[]{true,true,true,true,true,true,true});root.measure(View.MeasureSpec.makeMeasureSpec(pixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(Math.round(844*density),View.MeasureSpec.EXACTLY));root.layout(0,0,pixels,Math.round(844*density));verify(root);}}
  }
 }
 private void verify(View v){
  if(v.getVisibility()!=View.VISIBLE||v.getWidth()==0)return;
  if(v.getId()!=android.R.id.content&&v.getParent() instanceof ViewGroup){ViewGroup parent=(ViewGroup)v.getParent();assertTrue("Element exceeds horizontal grid: "+v.getClass().getSimpleName(),v.getLeft()>=0&&v.getRight()<=parent.getWidth());}
  if(v instanceof LinearLayout){LinearLayout row=(LinearLayout)v;if(row.getOrientation()==LinearLayout.HORIZONTAL&&row.getChildCount()==3&&row.getChildAt(0) instanceof ImageView&&row.getChildAt(1) instanceof TextView&&row.getChildAt(2).getClass()==View.class){View label=row.getChildAt(1);assertEquals("Granted permission text is not centered",row.getWidth(),label.getLeft()+label.getRight(),2);}}
  if(v instanceof Switch){ViewGroup row=(ViewGroup)v.getParent();assertTrue("Switch extends beyond row",v.getBottom()<=row.getHeight());for(int i=0;i<row.getChildCount();i++){View sibling=row.getChildAt(i);if(sibling instanceof TextView&&sibling!=v)assertTrue("Text overlaps switch",sibling.getRight()<=v.getLeft());}}
  if(v instanceof LinearLayout){LinearLayout row=(LinearLayout)v;if(row.getOrientation()==LinearLayout.VERTICAL&&row.getChildCount()==2&&row.getChildAt(0) instanceof ImageView&&row.getChildAt(1) instanceof TextView){View icon=row.getChildAt(0),label=row.getChildAt(1);assertEquals("Navigation centers differ",icon.getLeft()+icon.getRight(),label.getLeft()+label.getRight(),2);}}
  if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)verify(((ViewGroup)v).getChildAt(i));
 }
}
