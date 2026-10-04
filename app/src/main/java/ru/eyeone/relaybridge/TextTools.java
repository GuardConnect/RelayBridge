package ru.eyeone.relaybridge;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
final class TextTools {
    static String hash(String s) {
        try { byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder(); for(byte v:b) out.append(String.format("%02x",v & 255)); return out.toString();
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
    static int chunkEnd(String s, int start, int limit) {
        int end=Math.min(s.length(), start+limit);
        if(end<s.length() && end>start && Character.isHighSurrogate(s.charAt(end-1)) && Character.isLowSurrogate(s.charAt(end))) end--;
        return end;
    }
    static String cap(String s, int max) { return s.length()<=max?s:s.substring(0,chunkEnd(s,0,max))+"\n[Обрезано]"; }
}
