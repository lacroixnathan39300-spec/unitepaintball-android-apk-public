package com.unitepaintball.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import org.json.JSONObject;

/** Synchronise et met en cache la palette publique du site. */
public final class SiteThemeManager {
    private SiteThemeManager() {}
    private static final String PREFS = "up_site_theme";
    private static final String KEY_JSON = "theme_json";
    private static final String KEY_UPDATED = "theme_updated_at";
    public static final String ENDPOINT = "/api/app-theme";

    public static boolean applyCached(Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_JSON, "");
        if (raw == null || raw.trim().isEmpty()) return false;
        try { return applyThemeObject(new JSONObject(raw)); } catch (Exception ignored) { return false; }
    }

    /** Appel bloquant : à exécuter uniquement depuis un thread IO. */
    public static boolean syncNow(Context context, String token) throws Exception {
        JSONObject response = ApiClient.get(ENDPOINT, token == null ? "" : token);
        JSONObject source = response.optJSONObject("theme");
        if (source == null) source = response.optJSONObject("colors");
        if (source == null) source = response;
        JSONObject normalizedObject = normalize(source);
        if (!normalizedObject.has("primary") || !normalizedObject.has("background") || !normalizedObject.has("surface") || !normalizedObject.has("text"))
            throw new IllegalArgumentException("Palette site incomplète");
        String normalized = normalizedObject.toString();
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String previous = p.getString(KEY_JSON, "");
        p.edit().putString(KEY_JSON, normalized).putLong(KEY_UPDATED, System.currentTimeMillis()).apply();
        applyThemeObject(new JSONObject(normalized));
        return !normalized.equals(previous);
    }

    public static long lastUpdatedAt(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_UPDATED, 0L);
    }

    private static JSONObject normalize(JSONObject in) {
        JSONObject out = new JSONObject();
        try {
            put(out,"background",pick(in,"background","bg","theme_background"));
            put(out,"backgroundDeep",pick(in,"backgroundDeep","background_deep","bgDeep"));
            put(out,"surface",pick(in,"surface","card","theme_surface"));
            put(out,"surfaceAlt",pick(in,"surfaceAlt","surface_alt","card2"));
            put(out,"primary",pick(in,"primary","green","theme_primary"));
            put(out,"secondary",pick(in,"secondary","cyan","accent","theme_secondary"));
            put(out,"secondaryBright",pick(in,"secondaryBright","cyanBright","cyan_bright"));
            put(out,"text",pick(in,"text","theme_text"));
            put(out,"muted",pick(in,"muted","theme_muted"));
            put(out,"stroke",pick(in,"stroke","border","theme_stroke"));
            put(out,"success",pick(in,"success"));
            put(out,"danger",pick(in,"danger"));
            put(out,"epic",pick(in,"epic"));
            put(out,"legendary",pick(in,"legendary"));
            put(out,"mythic",pick(in,"mythic"));
            put(out,"amber",pick(in,"amber"));
            put(out,"primaryText",pick(in,"primaryText","primary_text"));
        } catch (Exception ignored) {}
        return out;
    }

    private static void put(JSONObject o, String key, String value) throws Exception { if (validHex(value)) o.put(key, value.toUpperCase()); }
    private static String pick(JSONObject o, String... keys) { for (String k:keys) { String v=o.optString(k,"").trim(); if(validHex(v))return v; } return ""; }
    private static boolean validHex(String v) { return v != null && v.matches("#[0-9a-fA-F]{6}"); }
    private static int color(JSONObject o,String key,int fallback){String v=o.optString(key,"");try{return validHex(v)?Color.parseColor(v):fallback;}catch(Exception e){return fallback;}}

    private static boolean applyThemeObject(JSONObject t) {
        if (t == null || t.length() == 0) return false;
        NeonPalette.BG = color(t,"background",NeonPalette.DEFAULT_BG);
        NeonPalette.BG_DEEP = color(t,"backgroundDeep",darken(NeonPalette.BG,0.62f));
        NeonPalette.SURFACE = color(t,"surface",NeonPalette.DEFAULT_SURFACE);
        NeonPalette.SURFACE_ALT = color(t,"surfaceAlt",mix(NeonPalette.SURFACE,NeonPalette.BG,0.22f));
        // Le site est la source de vérité de l’accent visuel de l’application.
        // Historiquement beaucoup de composants natifs utilisent CYAN comme variable
        // d’accent. En mode « Site synchronisé », CYAN doit donc suivre PRIMARY,
        // sinon une partie de l’UI reste cyan lorsque le site passe en orange/vert/etc.
        int sitePrimary = color(t,"primary",NeonPalette.DEFAULT_GREEN);
        NeonPalette.GREEN = sitePrimary;
        NeonPalette.CYAN = sitePrimary;
        NeonPalette.CYAN_BRIGHT = lighten(sitePrimary,0.25f);
        NeonPalette.ORANGE = sitePrimary;
        NeonPalette.TEXT = color(t,"text",NeonPalette.DEFAULT_TEXT);
        NeonPalette.MUTED = color(t,"muted",NeonPalette.DEFAULT_MUTED);
        NeonPalette.STROKE = color(t,"stroke",mix(NeonPalette.CYAN,NeonPalette.SURFACE,0.65f));
        NeonPalette.SUCCESS = color(t,"success",NeonPalette.DEFAULT_SUCCESS);
        NeonPalette.DANGER = color(t,"danger",NeonPalette.DEFAULT_DANGER);
        NeonPalette.EPIC = color(t,"epic",NeonPalette.DEFAULT_EPIC);
        NeonPalette.LEGENDARY = color(t,"legendary",NeonPalette.DEFAULT_LEGENDARY);
        NeonPalette.MYTHIC = color(t,"mythic",NeonPalette.DEFAULT_MYTHIC);
        NeonPalette.AMBER = color(t,"amber",NeonPalette.DEFAULT_AMBER);
        NeonPalette.COMMON = NeonPalette.MUTED;
        NeonPalette.UNCOMMON = NeonPalette.SUCCESS;
        NeonPalette.RARE = NeonPalette.CYAN;
        NeonPalette.PRIMARY_TEXT = color(t,"primaryText",bestForeground(NeonPalette.GREEN));
        return true;
    }

    private static int bestForeground(int bg){double y=(0.299*Color.red(bg)+0.587*Color.green(bg)+0.114*Color.blue(bg));return y>155?Color.rgb(2,6,9):Color.WHITE;}
    private static int darken(int c,float factor){return Color.rgb(Math.round(Color.red(c)*factor),Math.round(Color.green(c)*factor),Math.round(Color.blue(c)*factor));}
    private static int lighten(int c,float amount){return mix(c,Color.WHITE,amount);}
    private static int mix(int a,int b,float amount){float x=Math.max(0f,Math.min(1f,amount));return Color.rgb(Math.round(Color.red(a)*(1-x)+Color.red(b)*x),Math.round(Color.green(a)*(1-x)+Color.green(b)*x),Math.round(Color.blue(a)*(1-x)+Color.blue(b)*x));}
}
