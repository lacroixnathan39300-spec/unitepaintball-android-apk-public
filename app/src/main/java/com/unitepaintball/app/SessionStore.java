package com.unitepaintball.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.json.JSONArray;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SessionStore {
    private static final String PREFS = "unite_paintball";
    private static final String TOKEN_LEGACY = "session_token";
    private static final String TOKEN_ENCRYPTED = "session_token_encrypted";
    private static final String TOKEN_IV = "session_token_iv";
    private static final String CACHE = "last_sync";
    private static final String LAST_NOTIFICATION = "last_notification_id";
    private static final String PLAYERS_CACHE = "players_cache";
    private static final String LAST_SUCCESS_SYNC = "last_success_sync";
    private static final String SEEN_NOTIFICATIONS = "seen_notification_ids";
    private static final String KEY_ALIAS = "unite_paintball_session_key";
    private final SharedPreferences prefs;

    public SessionStore(Context c) {
        prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String token() {
        String encrypted = prefs.getString(TOKEN_ENCRYPTED, "");
        String iv = prefs.getString(TOKEN_IV, "");
        if (!encrypted.isEmpty() && !iv.isEmpty()) {
            try {
                return decrypt(encrypted, iv);
            } catch (Exception ignored) {
                clearToken();
                return "";
            }
        }

        // Migration transparente depuis les toutes premières builds de test.
        String legacy = prefs.getString(TOKEN_LEGACY, "");
        if (!legacy.isEmpty()) {
            saveToken(legacy);
            prefs.edit().remove(TOKEN_LEGACY).apply();
            return legacy;
        }
        return "";
    }

    public void saveToken(String value) {
        if (value == null || value.isEmpty()) {
            clearToken();
            return;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            prefs.edit()
                    .putString(TOKEN_ENCRYPTED, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .putString(TOKEN_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .remove(TOKEN_LEGACY)
                    .apply();
        } catch (Exception e) {
            // Ne jamais stocker le jeton en clair si le Keystore échoue.
            clearToken();
            throw new IllegalStateException("Impossible de sécuriser la session Android.", e);
        }
    }

    public void clearToken() {
        prefs.edit()
                .remove(TOKEN_LEGACY)
                .remove(TOKEN_ENCRYPTED)
                .remove(TOKEN_IV)
                .remove(CACHE)
                .remove(PLAYERS_CACHE)
                .remove(LAST_NOTIFICATION)
                .remove(LAST_SUCCESS_SYNC)
                .remove(SEEN_NOTIFICATIONS)
                .apply();
    }

    public String cache() { return prefs.getString(CACHE, ""); }
    public void saveCache(String json) { prefs.edit().putString(CACHE, json == null ? "" : json).apply(); }
    public String playersCache() { return prefs.getString(PLAYERS_CACHE, ""); }
    public void savePlayersCache(String json) { prefs.edit().putString(PLAYERS_CACHE, json == null ? "" : json).apply(); }
    public String lastNotificationId() { return prefs.getString(LAST_NOTIFICATION, ""); }
    public void saveLastNotificationId(String id) { prefs.edit().putString(LAST_NOTIFICATION, id == null ? "" : id).apply(); }
    public long lastSuccessSync() { return prefs.getLong(LAST_SUCCESS_SYNC, 0L); }
    public void saveLastSuccessSync(long when) { prefs.edit().putLong(LAST_SUCCESS_SYNC, Math.max(0L, when)).apply(); }

    public List<String> seenNotificationIds() {
        ArrayList<String> out = new ArrayList<>();
        String raw = prefs.getString(SEEN_NOTIFICATIONS, "");
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                String id = arr.optString(i, "");
                if (!id.isEmpty()) out.add(id);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public void rememberNotificationIds(List<String> ids) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(seenNotificationIds());
        if (ids != null) {
            for (String id : ids) if (id != null && !id.isEmpty()) merged.add(id);
        }
        while (merged.size() > 200) {
            String first = merged.iterator().next();
            merged.remove(first);
        }
        JSONArray arr = new JSONArray();
        for (String id : merged) arr.put(id);
        prefs.edit().putString(SEEN_NOTIFICATIONS, arr.toString()).apply();
    }

    public boolean hasSeenNotification(String id) {
        if (id == null || id.isEmpty()) return false;
        for (String seen : seenNotificationIds()) if (id.equals(seen)) return true;
        return false;
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private static String decrypt(String encodedCiphertext, String encodedIv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = Base64.decode(encodedIv, Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(encodedCiphertext, Base64.NO_WRAP);
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }
}
