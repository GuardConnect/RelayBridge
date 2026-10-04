package ru.eyeone.relaybridge;
import android.app.AlertDialog;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import java.lang.reflect.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class UiSelectionColorsTest {
 @Test public void smtpHighlightsOnlySelectedVariantWhenChoiceChanges() throws Exception {
  try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
   for(String name:new String[]{"tls","auth"}){
    Spinner spinner=(Spinner)field(controller.get(),name);ViewGroup parent=(ViewGroup)spinner.getParent();LinearLayout row=(LinearLayout)parent.getChildAt(parent.indexOfChild(spinner)+1);
    checkChoices(row,spinner.getSelectedItemPosition(),UiColors.FIELD);row.getChildAt(1).performClick();assertEquals(1,spinner.getSelectedItemPosition());checkChoices(row,1,UiColors.FIELD);
   }
  }
 }
 @Test public void journalHighlightsOnlyActiveFiltersAndAllowsClearingType() throws Exception {
  try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
   HistoryView history=(HistoryView)field(controller.get(),"history");Method render=HistoryView.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(history);
   LinearLayout[] metrics=(LinearLayout[])field(history,"metricViews");metrics[2].performClick();for(int i=0;i<metrics.length;i++)assertEquals(i==2?UiColors.ACCENT:UiColors.SURFACE,color(metrics[i]));
   Button[] types=(Button[])field(history,"typeButtons");types[1].performClick();for(int i=0;i<types.length;i++)assertEquals(i==1?UiColors.ACCENT:UiColors.SUCCESS_BG,color(types[i]));
   types[1].performClick();for(Button button:types)assertEquals(UiColors.SUCCESS_BG,color(button));
  }
 }
 @Test public void dialogConfirmationIsAccentAndCancelIsNeutral() throws Exception {
  try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
   AlertDialog dialog=UiLayout.dialogBuilder(controller.get()).setPositiveButton("Сохранить",null).setNegativeButton("Отмена",null).show();ShadowLooper.idleMainLooper();assertEquals(UiColors.ACCENT,color(dialog.getButton(-1)));assertEquals(UiColors.SURFACE,color(dialog.getButton(-2)));assertEquals(UiColors.TEXT,dialog.getButton(-2).getCurrentTextColor());dialog.dismiss();
  }
 }
 private void checkChoices(LinearLayout row,int selected,int idle){for(int i=0;i<row.getChildCount();i++){assertEquals(i==selected?UiColors.ACCENT:idle,color(row.getChildAt(i)));assertEquals(UiColors.TEXT,((TextView)row.getChildAt(i)).getCurrentTextColor());}}
 static int color(View v){Drawable d=v.getBackground();if(d instanceof RippleDrawable)d=((RippleDrawable)d).getDrawable(0);return ((GradientDrawable)d).getColor().getDefaultColor();}
 static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
}
