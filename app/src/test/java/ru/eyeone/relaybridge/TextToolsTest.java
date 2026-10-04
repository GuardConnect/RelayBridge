package ru.eyeone.relaybridge;
import org.junit.Test;
import static org.junit.Assert.*;
public class TextToolsTest {
    @Test public void telegramChunksPreserveEmojiAndFullMessage() {
        String message="a".repeat(3999)+"🙂"+"б".repeat(5000)+"🚀";
        StringBuilder restored=new StringBuilder();int offset=0,parts=0;
        while(offset<message.length()) {
            int end=TextTools.chunkEnd(message,offset,4000);
            assertTrue(end>offset);assertTrue(end-offset<=4000);
            assertFalse(Character.isHighSurrogate(message.charAt(end-1)));
            restored.append(message,offset,end);offset=end;parts++;
        }
        assertEquals(message,restored.toString());assertEquals(3,parts);
    }
    @Test public void capDoesNotSplitEmoji() {
        String capped=TextTools.cap("a".repeat(9)+"🙂zz",10);
        assertEquals("a".repeat(9)+"\n[Обрезано]",capped);
        assertEquals("Короткий текст",TextTools.cap("Короткий текст",32));
    }
    @Test public void eventFingerprintsAreStableAndDistinct() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",TextTools.hash("abc"));
        assertNotEquals(TextTools.hash("sms|sender|100|text"),TextTools.hash("sms|sender|101|text"));
        assertNotEquals(TextTools.hash("Заголовок\nПервый"),TextTools.hash("Заголовок\nВторой"));
    }
}
