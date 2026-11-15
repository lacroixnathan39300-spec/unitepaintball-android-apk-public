package com.unitepaintball.app;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.util.UUID;

/**
 * Pont FCM optionnel. L'application continue de fonctionner sans Firebase configuré :
 * le polling existant reste le filet de sécurité jusqu'à activation du push serveur.
 */
public final class FirebasePushManager {
    private static final String PREFS = "unite_paintball_push";
    private static final String INSTALLATION_ID = "installation_id";
    private static final String LAST_REGISTERED_TOKEN = "last_registered_token";
    private static final String LAST_REGISTERED_SESSION = "last_registered_session";

    private FirebasePushManager() {}

    public static boolean isConfigured(Context context) {
        try {
            FirebaseApp app = FirebaseApp.initializeApp(context);
            if (app != null) return true;
            FirebaseApp.getInstance();
            return true;
        } catch (Exception ignored) {}

        return !AppConfig.FIREBASE_APPLICATION_ID.isEmpty()
                && !AppConfig.FIREBASE_PROJECT_ID.isEmpty()
                && !AppConfig.FIREBASE_API_KEY.isEmpty();
    }

    private static FirebaseApp ensureFirebase(Context context) {
        try {
            FirebaseApp app = FirebaseApp.initializeApp(context);
            if (app != null) return app;
            return FirebaseApp.getInstance();
        } catch (Exception ignored) {}

        if (!isConfigured(context)) return null;
        try {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setApplicationId(AppConfig.FIREBASE_APPLICATION_ID)
                    .setProjectId(AppConfig.FIREBASE_PROJECT_ID)
                    .setApiKey(AppConfig.FIREBASE_API_KEY)
                    .setGcmSenderId(AppConfig.FIREBASE_SENDER_ID)
                    .build();
            return FirebaseApp.initializeApp(context, options);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void ensureRegistered(Context context) {
        SessionStore session = new SessionStore(context);
        String cookie = session.token();
        if (cookie.isEmpty()) return;
        FirebaseApp app = ensureFirebase(context);
        if (app == null) return;

        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                if (!task.isSuccessful()) return;
                String token = task.getResult();
                if (token == null || token.isEmpty()) return;
                registerTokenAsync(context.getApplicationContext(), cookie, token);
            });
        } catch (Exception ignored) {}
    }

    public static void onNewToken(Context context, String token) {
        if (token == null || token.isEmpty()) return;
        String cookie = new SessionStore(context).token();
        if (cookie.isEmpty()) return;
        registerTokenAsync(context.getApplicationContext(), cookie, token);
    }

    private static void registerTokenAsync(Context context, String cookie, String token) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String sessionFingerprint = Integer.toHexString(cookie.hashCode());
        if (token.equals(prefs.getString(LAST_REGISTERED_TOKEN, ""))
                && sessionFingerprint.equals(prefs.getString(LAST_REGISTERED_SESSION, ""))) {
            return;
        }

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject()
                        .put("action", "register")
                        .put("token", token)
                        .put("installationId", installationId(context))
                        .put("platform", "android")
                        .put("appVersion", AppConfig.APP_VERSION);
                ApiClient.post("/api/mobile-push", cookie, body);
                prefs.edit()
                        .putString(LAST_REGISTERED_TOKEN, token)
                        .putString(LAST_REGISTERED_SESSION, sessionFingerprint)
                        .apply();
            } catch (Exception ignored) {}
        }, "UP-PushRegister").start();
    }

    public static void unregisterBestEffort(Context context) {
        String cookie = new SessionStore(context).token();
        if (cookie.isEmpty()) {
            clearLocalRegistration(context);
            return;
        }
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject()
                        .put("action", "unregister")
                        .put("installationId", installationId(app));
                ApiClient.post("/api/mobile-push", cookie, body);
            } catch (Exception ignored) {
            } finally {
                clearLocalRegistration(app);
            }
        }, "UP-PushUnregister").start();
    }

    private static void clearLocalRegistration(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(LAST_REGISTERED_TOKEN)
                .remove(LAST_REGISTERED_SESSION)
                .apply();
    }

    public static String installationId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = prefs.getString(INSTALLATION_ID, "");
        if (!id.isEmpty()) return id;
        id = UUID.randomUUID().toString();
        prefs.edit().putString(INSTALLATION_ID, id).apply();
        return id;
    }
}
