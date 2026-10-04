package ru.eyeone.relaybridge;
import android.view.View;
import android.graphics.drawable.Drawable;
/** Keep grid insets stable when backgrounds or selected states change. */
final class UiLayout {
 static void background(View view,Drawable drawable){int left=view.getPaddingLeft(),top=view.getPaddingTop(),right=view.getPaddingRight(),bottom=view.getPaddingBottom();view.setBackground(drawable);view.setPadding(left,top,right,bottom);}
}
