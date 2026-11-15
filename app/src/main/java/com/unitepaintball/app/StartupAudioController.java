package com.unitepaintball.app;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;

/**
 * Lecteur unique partagé entre LauncherActivity et MainActivity.
 * La piste de démarrage ne repart plus de zéro lors du changement d'écran.
 */
public final class StartupAudioController {
    private static MediaPlayer player;
    private static boolean soundEnabled = true;

    private StartupAudioController() {}

    public static synchronized void startOrContinue(Context context, boolean enabled) {
        soundEnabled = enabled;
        try {
            if (player == null) {
                Context app = context.getApplicationContext();
                player = MediaPlayer.create(app, R.raw.loading_music);
                if (player == null) return;
                // Mix long fourni par l’utilisateur (~48 min 44 s). Lecture unique : aucune boucle courte.
                // Le même MediaPlayer est partagé entre LauncherActivity et MainActivity.
                player.setLooping(false);
            }
            float v = soundEnabled ? 0.20f : 0f;
            player.setVolume(v, v);
            if (!player.isPlaying() && player.getCurrentPosition() < Math.max(0, player.getDuration() - 250)) {
                player.start();
            }
        } catch (Exception ignored) {
            release();
        }
    }

    public static synchronized void setEnabled(boolean enabled) {
        soundEnabled = enabled;
        if (player != null) {
            try {
                float v = enabled ? 0.20f : 0f;
                player.setVolume(v, v);
            } catch (Exception ignored) {}
        }
    }

    public static synchronized boolean isPlaying() {
        try { return player != null && player.isPlaying(); } catch (Exception ignored) { return false; }
    }

    public static synchronized void release() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
    }
}
