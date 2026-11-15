package com.unitepaintball.app;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/** Réception des data-messages FCM même quand l'app n'est pas ouverte. */
public class UniteFirebaseMessagingService extends FirebaseMessagingService {
    @Override public void onNewToken(String token) {
        super.onNewToken(token);
        FirebasePushManager.onNewToken(this, token);
    }

    @Override public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        if (new SessionStore(this).token().isEmpty() || !NativeNotifications.canShow(this)) return;

        Map<String, String> data = message.getData();
        String id = data.get("id");
        String title = data.get("title");
        String body = data.get("body");
        String category = data.get("category");
        String targetUrl = data.get("targetUrl");
        boolean urgent = "true".equalsIgnoreCase(data.get("urgent"));

        RemoteMessage.Notification notification = message.getNotification();
        if ((title == null || title.isEmpty()) && notification != null) title = notification.getTitle();
        if ((body == null || body.isEmpty()) && notification != null) body = notification.getBody();

        if (title == null || title.isEmpty()) title = "Unité Paintball";
        if (body == null) body = "";
        if (category == null) category = "general";
        if (targetUrl == null) targetUrl = "";

        int notificationId = id == null || id.isEmpty()
                ? (int) (System.currentTimeMillis() & 0x7fffffff)
                : (id.hashCode() & 0x7fffffff);
        NativeNotifications.show(this, notificationId, title, body, category, targetUrl, urgent);

        if (id != null && !id.isEmpty()) {
            java.util.ArrayList<String> ids = new java.util.ArrayList<>();
            ids.add(id);
            new SessionStore(this).rememberNotificationIds(ids);
        }
    }
}
