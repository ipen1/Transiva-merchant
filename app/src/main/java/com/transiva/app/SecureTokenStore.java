package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Stores the authentication token encrypted with a hardware/OS backed Android Keystore key.
 * No secret key is written to SharedPreferences.
 */
public final class SecureTokenStore {
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "transiva_merchant_auth_v1";
    private static final String PREF = "transiva_secure_auth";
    private static final String KEY_CIPHER = "auth_token_cipher";
    private static final String KEY_IV = "auth_token_iv";

    private SecureTokenStore() {}

    public static synchronized boolean save(Context context, String token) {
        String clean = token == null ? "" : token.trim();
        if (clean.isEmpty()) {
            clear(context);
            return true;
        }
        try {
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(clean.getBytes(StandardCharsets.UTF_8));

            prefs(context).edit()
                    .putString(KEY_CIPHER, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .putString(KEY_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .commit();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static synchronized String get(Context context) {
        try {
            String cipherText = prefs(context).getString(KEY_CIPHER, "");
            String iv = prefs(context).getString(KEY_IV, "");
            if (cipherText == null || cipherText.isEmpty() || iv == null || iv.isEmpty()) return "";

            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
            keyStore.load(null);
            SecretKey key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            if (key == null) return "";

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            byte[] plain = cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP));
            return new String(plain, StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    public static synchronized void clear(Context context) {
        try { prefs(context).edit().clear().commit(); } catch (Exception ignored) {}
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
