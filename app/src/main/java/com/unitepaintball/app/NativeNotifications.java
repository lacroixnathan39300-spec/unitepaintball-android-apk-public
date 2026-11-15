package com.unitepaintball.app;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;

/** Notifications Android séparées par usage pour permettre des réglages son/vibration distincts. */
public final class NativeNotifications {
    public static final String CHANNEL_GENERAL = "up_general";
    public static final String CHANNEL_MESSAGES = "up_messages";
    public static final String CHANNEL_ADMIN = "up_admin";
    public static final String CHANNEL_URGENT = "up_urgent";
    private NativeNotifications() {}

    public static void init(Activity activity) {
        ensureChannels(activity);
        if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4021);
        }
    }

    public static boolean canShow(Context context) {
        NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm!=null && nm.areNotificationsEnabled() && (Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED);
    }

    private static void ensureChannels(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        create(nm, CHANNEL_GENERAL, "Unité Paintball · Général", "Réservations, QR, événements et informations", NotificationManager.IMPORTANCE_DEFAULT, true);
        create(nm, CHANNEL_MESSAGES, "Unité Paintball · Messages", "Messages privés, groupes et conversations", NotificationManager.IMPORTANCE_HIGH, true);
        create(nm, CHANNEL_ADMIN, "Unité Paintball · Administration", "Réservations administratives, adhésions, incidents et actions administrateur", NotificationManager.IMPORTANCE_HIGH, true);
        create(nm, CHANNEL_URGENT, "Unité Paintball · Urgent", "Alertes de sécurité, incident et actions urgentes", NotificationManager.IMPORTANCE_HIGH, true);
    }

    private static void create(NotificationManager nm, String id, String name, String description, int importance, boolean vibration) {
        NotificationChannel channel = new NotificationChannel(id, name, importance);
        channel.setDescription(description);
        channel.enableVibration(vibration);
        nm.createNotificationChannel(channel);
    }

    private static String channelFor(String category, boolean urgent) {
        if (urgent) return CHANNEL_URGENT;
        String c = category == null ? "" : category.toLowerCase(java.util.Locale.FRANCE);
        if (c.contains("message") || c.contains("chat") || c.contains("communication")) return CHANNEL_MESSAGES;
        if (c.contains("admin") || c.contains("incident") || c.contains("stock") || c.contains("finance") || c.contains("compta")) return CHANNEL_ADMIN;
        return CHANNEL_GENERAL;
    }

    public static void show(Context context, int notificationId, String title, String body) {
        show(context, notificationId, title, body, "general", "", false);
    }

    public static void show(Context context, int notificationId, String title, String body, String category, String targetUrl, boolean urgent) {
        if (!canShow(context)) return;
        ensureChannels(context);
        String channel = channelFor(category, urgent);
        Intent open = new Intent(context, LauncherActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.putExtra("target_url", targetUrl == null ? "" : targetUrl);
        open.putExtra("notification_category", category == null ? "general" : category);
        open.putExtra("notification_title", title == null ? "" : title);
        open.putExtra("notification_body", body == null ? "" : body);
        PendingIntent pi = PendingIntent.getActivity(context, notificationId, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new android.app.Notification.Builder(context, channel)
                : new android.app.Notification.Builder(context);
        builder.setSmallIcon(urgent ? android.R.drawable.stat_notify_error : android.R.drawable.ic_dialog_info)
                .setColor(NeonPalette.GREEN)
                .setOnlyAlertOnce(true)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setContentTitle(title == null || title.isEmpty() ? "Unité Paintball" : title)
                .setContentText(body == null ? "" : body)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(body == null ? "" : body))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setCategory(urgent ? android.app.Notification.CATEGORY_ALARM : android.app.Notification.CATEGORY_STATUS)
                .setPriority(urgent ? android.app.Notification.PRIORITY_MAX : android.app.Notification.PRIORITY_HIGH);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(notificationId, builder.build());
    }
}
