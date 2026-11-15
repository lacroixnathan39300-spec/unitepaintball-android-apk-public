package com.unitepaintball.app;

public final class AppConfig {
    private AppConfig() {}
    public static final String BASE_URL = "https://unite-paintball-jura.lacroixnathan39300.chatgpt.site";
    public static final String APP_VERSION = "2.2.72-work-site-app-parity";

    // FCM est volontairement optionnel : remplir ces valeurs lors de la création du projet Firebase.
    // Tant qu'elles sont vides, l'application conserve le système de synchronisation/polling existant.
    public static final String FIREBASE_APPLICATION_ID = "";
    public static final String FIREBASE_PROJECT_ID = "";
    public static final String FIREBASE_API_KEY = "";
    public static final String FIREBASE_SENDER_ID = "";
}
