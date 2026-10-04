package ru.eyeone.relaybridge;
import android.content.Context;
import android.widget.Switch;
/** Native switch interaction with flat accent controls. */
final class UiSwitch extends Switch {
    UiSwitch(Context context){
        super(context);float density=getResources().getDisplayMetrics().density;
        setShowText(false);setTextOn("");setTextOff("");setSwitchMinWidth(Math.round(68*density));setSplitTrack(false);setPadding(0,0,0,0);
        setElevation(0);setStateListAnimator(null);
        setThumbTintList(null);setTrackTintList(null);
        setThumbDrawable(new UiToggleArtwork(UiToggleArtwork.THUMB,density));setTrackDrawable(new UiToggleArtwork(UiToggleArtwork.TRACK,density));
    }
}
