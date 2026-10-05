package ru.eyeone.relaybridge;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES-GCM encryption of local settings, queue and log.
 * The key never leaves Android Keystore. Sealed form: {@code base64(iv):base64(ciphertext+tag)}.
 */
final class Crypto {
    private static final String STORE = "AndroidKeyStore";
    private static final String ALIAS = "relay-v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;

    private Crypto() { }

    private static synchronized SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance(STORE);
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE);
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            generator.generateKey();
        }
        SecretKey key = (SecretKey) store.getKey(ALIAS, null);
        if (key == null) throw new GeneralSecurityException("Keystore key is unavailable");
        return key;
    }

    static String seal(String plain) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        // Keystore generates a fresh random IV for every encryption.
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        return encode(cipher.getIV()) + ":" + encode(sealed);
    }

    static String open(String sealed) throws Exception {
        int separator = sealed.indexOf(':');
        if (separator < 0) throw new GeneralSecurityException("Malformed sealed value");
        byte[] iv = Base64.decode(sealed.substring(0, separator), Base64.NO_WRAP);
        byte[] data = Base64.decode(sealed.substring(separator + 1), Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
        return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
    }

    private static String encode(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }
}
