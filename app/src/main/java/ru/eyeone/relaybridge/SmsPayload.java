package ru.eyeone.relaybridge;

import android.content.Intent;
import android.telephony.SmsMessage;
import android.util.Base64;

/** Decode a complete SMS broadcast without discarding messages for missing OEM metadata. */
final class SmsPayload {
    final SmsMessage[] parts;
    private final String identity;
    private SmsPayload(SmsMessage[] parts,String identity){this.parts=parts;this.identity=identity;}

    static SmsPayload decode(Intent intent){
        Object raw=intent.getSerializableExtra("pdus");
        if(!(raw instanceof Object[])||((Object[])raw).length==0)
            throw new IllegalArgumentException("Android передал SMS без данных сообщения");
        Object[] pdus=(Object[])raw;
        SmsMessage[] parts=new SmsMessage[pdus.length];StringBuilder identity=new StringBuilder();
        String declared=intent.getStringExtra("format");
        if(declared!=null&&!declared.isBlank()&&!declared.equals("3gpp")&&!declared.equals("3gpp2"))
            throw new IllegalArgumentException("Android передал неизвестный формат SMS");
        String format=declared==null||declared.isBlank()?null:declared;
        for(int index=0;index<pdus.length;index++){
            if(!(pdus[index] instanceof byte[])||((byte[])pdus[index]).length==0)
                throw new IllegalArgumentException("Android передал повреждённую часть SMS");
            byte[] pdu=(byte[])pdus[index];
            parts[index]=parse(pdu,format==null?"3gpp":format);
            if(parts[index]==null&&format==null)parts[index]=parse(pdu,"3gpp2");
            if(parts[index]==null||parts[index].getMessageBody()==null)
                throw new IllegalArgumentException("Не удалось прочитать SMS целиком: часть "+(index+1)+" из "+pdus.length);
            identity.append(Base64.encodeToString(pdu,Base64.NO_WRAP)).append('|');
        }
        return new SmsPayload(parts,identity.toString());
    }
    private static SmsMessage parse(byte[] pdu,String format){
        try{return SmsMessage.createFromPdu(pdu,format);}catch(RuntimeException malformed){return null;}
    }
    String key(int subscription){return TextTools.hash("sms-pdu|"+subscription+'|'+identity);}
    String body(){StringBuilder text=new StringBuilder();for(SmsMessage part:parts)text.append(part.getMessageBody());return text.toString();}
}
