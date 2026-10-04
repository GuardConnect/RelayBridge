package ru.eyeone.relaybridge;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.Switch;
/** Opaque switch shapes independent of the device theme tint and alpha. */
final class UiSwitch extends Switch {
    UiSwitch(Context context){
        super(context);setShowText(false);setTextOn("");setTextOff("");setSwitchMinWidth(dp(48));setSplitTrack(false);setPadding(0,0,0,0);
        setThumbTintList(null);setTrackTintList(null);
        setThumbDrawable(states(shape(UiColors.TEXT,24,24),shape(0xFFCFCDD6,24,24)));
        setTrackDrawable(states(shape(UiColors.ACCENT,48,28),shape(0xFF414149,48,28)));
    }
    private StateListDrawable states(GradientDrawable on,GradientDrawable off){StateListDrawable d=new StateListDrawable();d.addState(new int[]{android.R.attr.state_checked},on);d.addState(new int[]{},off);return d;}
    private GradientDrawable shape(int color,int width,int height){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setSize(dp(width),dp(height));d.setCornerRadius(dp(height)/2f);return d;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
