package ru.eyeone.relaybridge;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SubscriptionManager;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class SmsCaptureTest {
    private Application app;
    @Before public void setup(){
        app=RuntimeEnvironment.getApplication();
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_PHONE_STATE);
        cards(11,22);
    }
    private void cards(int... ids){
        android.telephony.SubscriptionInfo[] infos=new android.telephony.SubscriptionInfo[ids.length];
        for(int index=0;index<ids.length;index++)infos[index]=SubscriptionInfoBuilder.newBuilder()
                .setId(ids[index]).setSimSlotIndex(index).setCarrierName("Test").buildSubscriptionInfo();
        Shadows.shadowOf(app.getSystemService(SubscriptionManager.class)).setActiveSubscriptionInfos(infos);
    }
    private static Intent sms(String format,byte[]... pdus){
        Intent intent=new Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
        intent.putExtra("pdus",pdus);if(format!=null)intent.putExtra("format",format);return intent;
    }
    // Actual GSM SMS-DELIVER PDUs with the same service-centre timestamp and UCS-2 text.
    private static byte[] pdu(String sender,String text){
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        bytes.write(0);bytes.write(4);bytes.write(sender.length());bytes.write(0x91);
        for(int index=0;index<sender.length();index+=2){
            int first=sender.charAt(index)-'0';int second=index+1<sender.length()?sender.charAt(index+1)-'0':15;
            bytes.write(first|(second<<4));
        }
        bytes.write(0);bytes.write(8);
        for(int value:new int[]{0x62,0x01,0x50,0x90,0x95,0x04,0x44})bytes.write(value);
        byte[] body=text.getBytes(StandardCharsets.UTF_16BE);bytes.write(body.length);bytes.writeBytes(body);return bytes.toByteArray();
    }
    @Test public void twoSendersWithSameTextAndTimestampAreBothDecodedAndNeverDeduplicated(){
        SmsPayload first=SmsPayload.decode(sms("3gpp",pdu("61447369039","Тест")));
        SmsPayload second=SmsPayload.decode(sms("3gpp",pdu("61447369040","Тест")));
        assertEquals("+61447369039",first.parts[0].getOriginatingAddress());
        assertEquals("+61447369040",second.parts[0].getOriginatingAddress());
        assertEquals("Тест",first.body());assertEquals("Тест",second.body());
        assertNotEquals(first.key(22),second.key(22));
        assertEquals(first.key(22),SmsPayload.decode(sms("3gpp",pdu("61447369039","Тест"))).key(22));
    }
    @Test public void missingOrBlankFormatDoesNotDropGsmSms(){
        for(String format:new String[]{null,""})assertEquals("Привет",SmsPayload.decode(sms(format,pdu("61447369039","Привет"))).body());
    }
    @Test public void allPartsArePreservedAndAnInvalidPartIsNotSentAsPartialSms(){
        assertEquals("ПерваяВторая",SmsPayload.decode(sms("3gpp",pdu("61447369039","Первая"),pdu("61447369039","Вторая"))).body());
        assertThrows(IllegalArgumentException.class,()->SmsPayload.decode(sms("3gpp",pdu("61447369039","Первая"),new byte[0])));
        assertThrows(IllegalArgumentException.class,()->SmsPayload.decode(sms("3gpp")));
        assertThrows(IllegalArgumentException.class,()->SmsPayload.decode(sms("unexpected",pdu("61447369039","Тест"))));
    }
    @Test public void subscriptionAcceptsIntegerAndLongWithoutConfusingSlotWithId(){
        assertEquals(22,SimCards.smsSubscription(app,new Intent().putExtra("subscription",22L)));
        assertEquals(11,SimCards.smsSubscription(app,new Intent().putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,11)));
        assertEquals(22,SimCards.smsSubscription(app,new Intent().putExtra(SubscriptionManager.EXTRA_SLOT_INDEX,1)));
        assertEquals(22,SimCards.smsSubscription(app,new Intent().putExtra("slot",1L)));
        assertEquals(11,SimCards.smsSubscription(app,new Intent().putExtra("phone",0)));
    }
    @Test public void singleSimCanBeResolvedButDualSimUnknownAndBadSlotsAreNotGuessed(){
        assertEquals(-1,SimCards.smsSubscription(app,new Intent()));
        cards(22);assertEquals(22,SimCards.smsSubscription(app,new Intent()));
        assertEquals(-1,SimCards.smsSubscription(app,new Intent().putExtra("slot",5)));
        assertEquals(-1,SimCards.smsSubscription(app,new Intent().putExtra("subscription",Long.MAX_VALUE).putExtra("slot",5)));
    }
    @Test public void simFilterAcceptsResolvedSmsOnSelectedCardOnly(){
        int subscription=SimCards.smsSubscription(app,new Intent().putExtra("subscription",22L));
        assertTrue(SimRules.accepts(false,java.util.Set.of(22),subscription));
        assertFalse(SimRules.accepts(false,java.util.Set.of(11),subscription));
        assertTrue(SimRules.accepts(true,java.util.Set.of(),-1));
    }
}
