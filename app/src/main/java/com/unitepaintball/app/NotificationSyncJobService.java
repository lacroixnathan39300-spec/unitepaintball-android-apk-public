package com.unitepaintball.app;

import android.app.job.JobParameters;
import android.app.job.JobService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Synchronisation périodique native Android. Elle remplace WorkManager dans
 * les IDE qui ne packagent pas correctement AndroidX Startup/Core.
 */
public final class NotificationSyncJobService extends JobService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public boolean onStartJob(JobParameters params) {
        executor.execute(() -> {
            boolean retry = syncNotifications();
            jobFinished(params, retry);
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private boolean syncNotifications() {
        if (!NativeNotifications.canShow(this)) return false;
        SessionStore store = new SessionStore(getApplicationContext());
        String session = store.token();
        if (session == null || session.isEmpty()) return false;

        try {
            JSONObject response = ApiClient.get("/api/player-notifications", session);
            JSONArray rows = response.optJSONArray("rows");
            if (rows == null || rows.length() == 0) return false;

            java.util.List<String> seenBefore = store.seenNotificationIds();
            java.util.HashSet<String> seen = new java.util.HashSet<>(seenBefore);
            java.util.ArrayList<String> pageIds = new java.util.ArrayList<>();

            // Première exécution : mémoriser la page actuelle sans republier tout l'historique.
            if (seenBefore.isEmpty()) {
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.optJSONObject(i);
                    if (row == null) continue;
                    String id = notificationIdOf(row);
                    if (!id.isEmpty()) pageIds.add(id);
                }
                store.rememberNotificationIds(pageIds);
                JSONObject first = rows.optJSONObject(0);
                if (first != null) store.saveLastNotificationId(notificationIdOf(first));
                return false;
            }

            // Parcours ancien -> récent pour conserver un ordre naturel dans le volet Android.
            for (int i = rows.length() - 1; i >= 0; i--) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                String id = notificationIdOf(row);
                if (id.isEmpty()) continue;
                pageIds.add(id);
                if (seen.contains(id) || row.optBoolean("read", false)) continue;

                String category = row.optString("category", "general");
                String targetUrl = row.optString("targetUrl", "");
                String hay = (category + " " + row.optString("title", "") + " " + row.optString("body", "")).toLowerCase(java.util.Locale.FRANCE);
                boolean urgent = hay.contains("urgent") || hay.contains("incident") || hay.contains("sécurité") || hay.contains("securite") || hay.contains("erreur") || hay.contains("annul");
                NativeNotifications.show(
                        getApplicationContext(),
                        stableNotificationId(id),
                        row.optString("title", "Unité Paintball"),
                        row.optString("body", "Nouvelle activité sur Unité Paintball."),
                        category, targetUrl, urgent
                );
            }

            store.rememberNotificationIds(pageIds);
            JSONObject first = rows.optJSONObject(0);
            if (first != null) store.saveLastNotificationId(notificationIdOf(first));
            store.saveLastSuccessSync(System.currentTimeMillis());
            return false;
        } catch (ApiClient.ApiException e) {
            return e.status != 401;
        } catch (Exception e) {
            return true;
        }
    }

    private static String notificationIdOf(JSONObject row) {
        Object raw = row == null ? null : row.opt("id");
        return raw == null || raw == JSONObject.NULL ? "" : String.valueOf(raw);
    }

    private static int stableNotificationId(String id) {
        int value = id == null ? 0 : id.hashCode();
        if (value == Integer.MIN_VALUE) value = 1;
        return Math.abs(value == 0 ? 1 : value);
    }
}
