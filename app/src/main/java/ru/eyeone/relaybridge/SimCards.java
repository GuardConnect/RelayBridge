package ru.eyeone.relaybridge;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import java.util.*;
final class SimCards {
    static final class Card {final int id,slot;final String label;Card(int id,int slot,CharSequence carrier){this.id=id;this.slot=slot;label=SimRules.label(slot,carrier);}}
    static boolean permitted(Context c){return c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED;}
    static List<Card> active(Context c){
        if(c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED)return Collections.emptyList();
        List<Card> cards=new ArrayList<>();
        try{SubscriptionManager manager=c.getSystemService(SubscriptionManager.class);if(manager==null)return cards;
            List<SubscriptionInfo> infos=manager.getActiveSubscriptionInfoList();if(infos!=null)for(SubscriptionInfo info:infos)cards.add(new Card(info.getSubscriptionId(),info.getSimSlotIndex(),info.getCarrierName()));
        }catch(SecurityException|UnsupportedOperationException ignored){}
        cards.sort(Comparator.comparingInt((Card card)->card.slot<0?Integer.MAX_VALUE:card.slot).thenComparingInt(card->card.id));return cards;
    }
    static int subscription(Intent intent){int id=intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,-1);return id>=0?id:intent.getIntExtra("subscription",-1);}
    // Some modem/OEM SMS broadcasts use a Long or supply only a zero-based slot.
    static int smsSubscription(Context c,Intent intent){
        for(String key:new String[]{SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,"subscription",android.telephony.TelephonyManager.EXTRA_SUBSCRIPTION_ID}){
            int id=numberExtra(intent,key);if(id>=0)return id;
        }
        List<Card> cards=active(c);
        for(String key:new String[]{SubscriptionManager.EXTRA_SLOT_INDEX,"slot","phone"}){
            int slot=numberExtra(intent,key);if(slot<0)continue;
            int found=-1;for(Card card:cards)if(card.slot==slot){if(found>=0)return -1;found=card.id;}
            if(found>=0)return found;
            return -1; // An explicit but unmapped slot must not select a different SIM.
        }
        return cards.size()==1?cards.get(0).id:-1;
    }
    private static int numberExtra(Intent intent,String key){
        android.os.Bundle extras=intent.getExtras();Object value=extras==null?null:extras.get(key);
        if(!(value instanceof Integer)&&!(value instanceof Long))return -1;
        long number=((Number)value).longValue();return number>=0&&number<=Integer.MAX_VALUE?(int)number:-1;
    }
    // PHONE_STATE does not guarantee subscription extras on every device.
    static int callSubscription(Context c,Intent intent){
        List<Card> cards=active(c);
        int[] candidates=new int[cards.size()];int count=0;
        for(String key:new String[]{android.telephony.TelephonyManager.EXTRA_SUBSCRIPTION_ID,SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,"subscription"}){
            int id=intent.getIntExtra(key,-1);
            if(id>=0)for(Card card:cards)if(card.id==id)return id;
        }
        if(!android.telephony.TelephonyManager.EXTRA_STATE_RINGING.equals(intent.getStringExtra(android.telephony.TelephonyManager.EXTRA_STATE)))return -1;
        android.telephony.TelephonyManager manager=c.getSystemService(android.telephony.TelephonyManager.class);
        if(manager==null)return -1;
        try{for(Card card:cards)if(manager.createForSubscriptionId(card.id).getCallStateForSubscription()==android.telephony.TelephonyManager.CALL_STATE_RINGING)candidates[count++]=card.id;}
        catch(SecurityException|UnsupportedOperationException ignored){return -1;}
        return SimRules.unique(candidates,count);
    }
    static String label(Context c,int subscription){for(Card card:active(c))if(card.id==subscription)return card.label;return SimRules.label(-1,null);}
}
