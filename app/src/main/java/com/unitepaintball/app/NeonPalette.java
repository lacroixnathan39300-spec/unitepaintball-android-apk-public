package com.unitepaintball.app;

import android.graphics.Color;

/**
 * Palette Unité Paintball. Depuis 2.2.36, ces valeurs peuvent être remplacées
 * à l’exécution par le thème actif du site. Les DEFAULT_* restent le filet de sécurité hors ligne.
 */
public final class NeonPalette {
    private NeonPalette() {}

    public static final int DEFAULT_BG = Color.rgb(5, 9, 13);
    public static final int DEFAULT_BG_DEEP = Color.rgb(2, 6, 9);
    public static final int DEFAULT_SURFACE = Color.rgb(13, 32, 41);
    public static final int DEFAULT_SURFACE_ALT = Color.rgb(16, 42, 52);
    public static final int DEFAULT_GREEN = Color.rgb(157, 255, 53);
    public static final int DEFAULT_CYAN = Color.rgb(32, 229, 243);
    public static final int DEFAULT_CYAN_BRIGHT = Color.rgb(99, 244, 255);
    public static final int DEFAULT_AMBER = Color.rgb(255, 194, 71);
    public static final int DEFAULT_SUCCESS = Color.rgb(69, 230, 138);
    public static final int DEFAULT_DANGER = Color.rgb(255, 70, 85);
    public static final int DEFAULT_EPIC = Color.rgb(169, 112, 255);
    public static final int DEFAULT_LEGENDARY = Color.rgb(255, 212, 90);
    public static final int DEFAULT_MYTHIC = Color.rgb(255, 65, 108);
    public static final int DEFAULT_TEXT = Color.rgb(244, 248, 251);
    public static final int DEFAULT_MUTED = Color.rgb(143, 163, 174);
    public static final int DEFAULT_STROKE = Color.rgb(40, 70, 83);

    public static int BG = DEFAULT_BG;
    public static int BG_DEEP = DEFAULT_BG_DEEP;
    public static int SURFACE = DEFAULT_SURFACE;
    public static int SURFACE_ALT = DEFAULT_SURFACE_ALT;
    public static int GREEN = DEFAULT_GREEN;
    public static int CYAN = DEFAULT_CYAN;
    public static int CYAN_BRIGHT = DEFAULT_CYAN_BRIGHT;
    /** Alias historique. L’UI ne doit plus imposer une couleur orange. */
    public static int ORANGE = DEFAULT_CYAN;
    public static int AMBER = DEFAULT_AMBER;
    public static int SUCCESS = DEFAULT_SUCCESS;
    public static int COMMON = DEFAULT_MUTED;
    public static int UNCOMMON = DEFAULT_SUCCESS;
    public static int RARE = DEFAULT_CYAN;
    public static int DANGER = DEFAULT_DANGER;
    public static int EPIC = DEFAULT_EPIC;
    public static int LEGENDARY = DEFAULT_LEGENDARY;
    public static int MYTHIC = DEFAULT_MYTHIC;
    public static int TEXT = DEFAULT_TEXT;
    public static int MUTED = DEFAULT_MUTED;
    public static int STROKE = DEFAULT_STROKE;
    public static int PRIMARY_TEXT = DEFAULT_BG_DEEP;

    public static void resetDefaults() {
        BG = DEFAULT_BG; BG_DEEP = DEFAULT_BG_DEEP; SURFACE = DEFAULT_SURFACE; SURFACE_ALT = DEFAULT_SURFACE_ALT;
        GREEN = DEFAULT_GREEN; CYAN = DEFAULT_CYAN; CYAN_BRIGHT = DEFAULT_CYAN_BRIGHT; ORANGE = DEFAULT_CYAN;
        AMBER = DEFAULT_AMBER; SUCCESS = DEFAULT_SUCCESS; COMMON = DEFAULT_MUTED; UNCOMMON = DEFAULT_SUCCESS;
        RARE = DEFAULT_CYAN; DANGER = DEFAULT_DANGER; EPIC = DEFAULT_EPIC; LEGENDARY = DEFAULT_LEGENDARY;
        MYTHIC = DEFAULT_MYTHIC; TEXT = DEFAULT_TEXT; MUTED = DEFAULT_MUTED; STROKE = DEFAULT_STROKE;
        PRIMARY_TEXT = DEFAULT_BG_DEEP;
    }
}
