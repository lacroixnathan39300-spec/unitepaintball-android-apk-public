package com.unitepaintball.app;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

/** Programme la vérification périodique des notifications du site en arrière-plan. */
public final class BackgroundSyncScheduler {
    private static final int JOB_ID = 3930015;
    private static final int JOB_ID_SOON = 3930016;
    private static final long INTERVAL_MS = 15L * 60L * 1000L;

    private BackgroundSyncScheduler() {}

    public static void ensureScheduled(Context context) {
        if (!context.getSharedPreferences("up_settings", Context.MODE_PRIVATE).getBoolean("background_sync", true)) return;
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;

        for (JobInfo pending : scheduler.getAllPendingJobs()) {
            if (pending.getId() == JOB_ID) return;
        }

        ComponentName service = new ComponentName(context, NotificationSyncJobService.class);
        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, service)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setPeriodic(INTERVAL_MS);

        if (Build.VERSION.SDK_INT >= 31) {
            builder.setExpedited(false);
        }

        try {
            scheduler.schedule(builder.build());
        } catch (SecurityException ignored) {
            // Ne jamais faire planter l’application si une ROM/installation refuse la contrainte réseau.
        }
    }
    public static void setEnabled(Context context, boolean enabled) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) {
            if (!enabled) { scheduler.cancel(JOB_ID); scheduler.cancel(JOB_ID_SOON); }
            else ensureScheduled(context);
        }
    }

    /** Lance une vérification unique peu après le passage de l'app en arrière-plan. */
    public static void scheduleSoon(Context context) {
        if (!context.getSharedPreferences("up_settings", Context.MODE_PRIVATE).getBoolean("background_sync", true)) return;
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        try {
            ComponentName service = new ComponentName(context, NotificationSyncJobService.class);
            JobInfo.Builder builder = new JobInfo.Builder(JOB_ID_SOON, service)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setMinimumLatency(10_000L)
                    .setOverrideDeadline(60_000L)
                    .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL);
            scheduler.schedule(builder.build());
        } catch (Exception ignored) {
            // Une ROM peut refuser/reporter ce job : le job périodique reste la sécurité de fond.
        }
    }

}
