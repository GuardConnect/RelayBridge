package ru.eyeone.relaybridge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Small text helpers shared by capture and delivery. */
final class TextTools {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private TextTools() { }

    /** Lower-case hexadecimal SHA-256 of the UTF-8 form of {@code value}. */
    static String hash(String value) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        char[] out = new char[digest.length * 2];
        for (int index = 0; index < digest.length; index++) {
            out[index * 2] = HEX[(digest[index] >> 4) & 0x0F];
            out[index * 2 + 1] = HEX[digest[index] & 0x0F];
        }
        return new String(out);
    }

    /**
     * End offset of a chunk of at most {@code limit} UTF-16 units starting at {@code start}.
     * A surrogate pair is never split between two chunks.
     */
    static int chunkEnd(String text, int start, int limit) {
        int end = Math.min(text.length(), start + limit);
        boolean splitsPair = end < text.length() && end > start
                && Character.isHighSurrogate(text.charAt(end - 1))
                && Character.isLowSurrogate(text.charAt(end));
        return splitsPair ? end - 1 : end;
    }

    /** Shorten {@code text} to {@code max} UTF-16 units and mark that it was cut. */
    static String cap(String text, int max) {
        if (text.length() <= max) return text;
        return text.substring(0, chunkEnd(text, 0, max)) + "\n[Обрезано]";
    }
}
