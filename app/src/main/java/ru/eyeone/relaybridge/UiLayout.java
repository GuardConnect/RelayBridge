package ru.eyeone.relaybridge;
import android.view.View;
import android.graphics.drawable.Drawable;
/** Keep grid insets stable when backgrounds or selected states change. */
final class UiLayout {
 static void background(View view,Drawable drawable){int left=view.getPaddingLeft(),top=view.getPaddingTop(),right=view.getPaddingRight(),bottom=view.getPaddingBottom();view.setBackground(drawable);view.setPadding(left,top,right,bottom);}
 static android.app.AlertDialog.Builder dialogBuilder(android.content.Context context){return new android.app.AlertDialog.Builder(context){@Override public android.app.AlertDialog create(){android.app.AlertDialog dialog=super.create();dialog.setOnShowListener(ignored->styleDialogButtons(dialog));return dialog;}};}
 static void styleDialogButtons(android.app.AlertDialog dialog){
  for(int which:new int[]{-1,-2,-3}){android.widget.Button button=dialog.getButton(which);if(button==null||button.getVisibility()!=View.VISIBLE)continue;
   float density=button.getResources().getDisplayMetrics().density;android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setColor(which==-1?UiColors.ACCENT:UiColors.SURFACE);shape.setStroke(Math.round(density),which==-1?UiColors.ACCENT:UiColors.BORDER);shape.setCornerRadius(12*density);background(button,new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33FFFFFF),shape,null));button.setTextColor(UiColors.TEXT);button.setBackgroundTintList(null);
   if(button.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams){android.view.ViewGroup.MarginLayoutParams lp=(android.view.ViewGroup.MarginLayoutParams)button.getLayoutParams();lp.leftMargin=Math.round(4*density);lp.rightMargin=Math.round(4*density);button.setLayoutParams(lp);}
  }
 }
}
