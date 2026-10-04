package ru.eyeone.relaybridge;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.Switch;
/** Padded, opaque controls with native switch gestures, animation and accessibility. */
final class UiSwitch extends Switch {
    UiSwitch(Context context){
        super(context);setShowText(false);setTextOn("");setTextOff("");setSwitchMinWidth(dp(56));setSplitTrack(false);setPadding(0,0,0,0);
        setThumbTintList(null);setTrackTintList(null);
        setThumbDrawable(states(thumb(UiColors.TEXT,0xFFE1F3FF),thumb(0xFFD9DFE8,0xFFBAC4D2),thumb(0xFF8E98A6,0xFF8E98A6)));
        setTrackDrawable(states(track(new int[]{UiColors.ACCENT,0xFF1489DF},0xFF34BAF8),track(new int[]{0xFF252D38,0xFF303B48},0xFF485464),track(new int[]{0xFF20252D,0xFF20252D},0xFF353E49)));
    }
    private StateListDrawable states(Drawable on,Drawable off,Drawable disabled){StateListDrawable d=new StateListDrawable();d.addState(new int[]{-android.R.attr.state_enabled},disabled);d.addState(new int[]{android.R.attr.state_checked},on);d.addState(new int[]{},off);return d;}
    private Drawable thumb(int fill,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setSize(dp(24),dp(24));d.setCornerRadius(dp(12));d.setStroke(dp(1),stroke);return new InsetDrawable(d,0,dp(4),0,dp(4));}
    private GradientDrawable track(int[] colors,int stroke){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,colors);d.setSize(dp(56),dp(32));d.setCornerRadius(dp(16));d.setStroke(dp(1),stroke);d.setPadding(dp(4),0,dp(4),0);return d;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
