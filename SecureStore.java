package com.vtuber.overlay;

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
 * Guarda os dados da Live. O endereço do servidor é texto comum; a chave de transmissão só existe no
 * aparelho cifrada (AES-256-GCM) com uma chave do Android Keystore, que nunca sai do hardware/sistema.
 * Nada daqui é escrito em log. O app já tem allowBackup=false, então a chave cifrada também não vai para backup.
 */
final class SecureStore {
    static final String DEFAULT_SERVER = "rtmps://a.rtmps.youtube.com:443/live2";

    private static final String PREFS = "vtuber_live";
    private static final String K_SERVER = "server";
    private static final String K_KEY = "key_enc";
    private static final String ALIAS = "vtuberlab_live_key_v1";
    private static final String PROVIDER = "AndroidKeyStore";

    private SecureStore() { }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String server(Context c) {
        String s = prefs(c).getString(K_SERVER, null);
        return s == null || s.trim().isEmpty() ? DEFAULT_SERVER : s;
    }

    static void saveServer(Context c, String server) {
        prefs(c).edit().putString(K_SERVER, server).apply();
    }

    static boolean hasKey(Context c) {
        return prefs(c).getString(K_KEY, null) != null;
    }

    static void clearKey(Context c) {
        prefs(c).edit().remove(K_KEY).apply();
        try {
            KeyStore ks = KeyStore.getInstance(PROVIDER);
            ks.load(null);
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS);
        } catch (Exception ignored) { }
    }

    private static SecretKey secretKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(PROVIDER);
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER);
            kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            kg.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS, null);
    }

    /** Cifra e grava a chave. Lança exceção se o aparelho não conseguir (nunca grava em texto puro). */
    static void saveKey(Context c, String key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey());
        byte[] iv = cipher.getIV();
        byte[] ct = cipher.doFinal(key.getBytes(StandardCharsets.UTF_8));
        String packed = Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP);
        if (!prefs(c).edit().putString(K_KEY, packed).commit()) throw new Exception("falha ao gravar");
    }

    /** Devolve a chave em texto, ou null se não existir / não puder ser decifrada. */
    static String loadKey(Context c) {
        String packed = prefs(c).getString(K_KEY, null);
        if (packed == null) return null;
        try {
            int i = packed.indexOf(':');
            if (i <= 0) return null;
            byte[] iv = Base64.decode(packed.substring(0, i), Base64.NO_WRAP);
            byte[] ct = Base64.decode(packed.substring(i + 1), Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** Endereço completo (servidor + "/" + chave) para o encoder. Nunca registre este valor. */
    static String endpoint(Context c) {
        String key = loadKey(c);
        if (key == null || key.isEmpty()) return null;
        String s = server(c).trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s + "/" + key;
    }
}
