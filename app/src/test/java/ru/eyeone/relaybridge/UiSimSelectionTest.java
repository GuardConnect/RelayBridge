package ru.eyeone.relaybridge;
import android.view.Gravity;
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
public class UiSimSelectionTest {
 @Test public void eachSelectedSimHasSeparateCardForSmsAndCalls() throws Exception {
  try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity a=controller.get();
   for(String field:new String[]{"simList","callSimList"}){
    LinearLayout list=list(a,field);render(a,list,false,new HashSet<>(Arrays.asList(10,20)),true);
    assertEquals(LinearLayout.VERTICAL,list.getOrientation());assertEquals(2,list.getChildCount());
    assertEquals("Sim 1 Alpha",title(list,0));assertEquals("Sim 2 Beta",title(list,1));
   }
  }
 }
 @Test public void allSimsShowsIndividualCardsAndFutureSimHint() throws Exception {
  try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity a=controller.get();LinearLayout list=list(a,"simList");render(a,list,true,Collections.emptySet(),true);
   assertEquals(3,list.getChildCount());assertTrue(list.getChildAt(0) instanceof LinearLayout);assertTrue(list.getChildAt(1) instanceof LinearLayout);assertTrue(list.getChildAt(2) instanceof TextView);
  }
 }
 @Test public void unavailableSelectedSimRetainsOwnCard() throws Exception {
  try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity a=controller.get();LinearLayout list=list(a,"simList");render(a,list,false,new HashSet<>(Arrays.asList(10,30)),true);
   assertEquals(2,list.getChildCount());assertEquals("SIM · ID 30",title(list,1));
  }
 }
 @Test public void connectionHintsKeepWhiteTextAndCenteredAlignment() throws Exception {
  try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
   for(String name:new String[]{"tgResult","mailResult"}){Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);TextView t=(TextView)f.get(controller.get());assertEquals(UiColors.TEXT,t.getCurrentTextColor());assertEquals(Gravity.CENTER,t.getGravity());assertEquals(-1,t.getLayoutParams().width);}
  }
 }
 private LinearLayout list(MainActivity a,String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return (LinearLayout)f.get(a);}
 private void render(MainActivity a,LinearLayout list,boolean all,Set<Integer> selected,boolean permitted)throws Exception{
  Method m=MainActivity.class.getDeclaredMethod("renderSimSelection",LinearLayout.class,String.class,boolean.class,Set.class,List.class,boolean.class);m.setAccessible(true);m.invoke(a,list,"SMS",all,selected,Arrays.asList(new SimCards.Card(10,0,"Alpha"),new SimCards.Card(20,1,"Beta")),permitted);
 }
 private String title(LinearLayout list,int index){LinearLayout row=(LinearLayout)list.getChildAt(index);LinearLayout words=(LinearLayout)row.getChildAt(1);return ((TextView)words.getChildAt(0)).getText().toString();}
}
