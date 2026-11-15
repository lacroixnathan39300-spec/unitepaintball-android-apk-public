package com.unitepaintball.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.window.OnBackInvokedCallback;
import android.widget.*;
import android.webkit.CookieManager;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_GALLERY_IMAGE = 5102;
    private static final int REQ_GALLERY_VIDEO = 5106;
    private static final int REQ_CHAT_IMAGE = 5103;
    private static final int REQ_AVATAR_IMAGE = 5104;
    private static final int REQ_ACCOUNTING_EXPORT = 5105;
    private static final int REQ_PRIVACY_EXPORT = 5107;
    private static final int REQ_ACCOUNTING_DOCUMENT_SAVE = 5108;
    private static final int REQ_ADMIN_BACKUP_SAVE = 5109;
    private static final int REQ_ADMIN_BACKUP_RESTORE = 5110;
    private static final String OWNER_EMAIL = "lacroixnathan39300@gmail.com";
    // Palette locale modifiable depuis Paramètres. Le mode sombre reste la référence visuelle du site.
    private static int BG = NeonPalette.BG;
    private static int CARD = NeonPalette.SURFACE;
    private static int CARD_2 = NeonPalette.SURFACE_ALT;
    private static int LIME = NeonPalette.GREEN;
    private static int PRIMARY_TEXT = NeonPalette.PRIMARY_TEXT;
    private static int ORANGE = NeonPalette.CYAN;
    private static int ORANGE_DARK = Color.rgb(14,119,132);
    private static int TEXT = NeonPalette.TEXT;
    private static int MUTED = NeonPalette.MUTED;
    private static int STROKE = Color.argb(26,255,255,255);
    private static final long MIN_NATIVE_STARTUP_MS = 900L;

    private static final class ParticipantForm {
        LinearLayout card;
        LinearLayout guardianSection;
        TextView title;
        EditText firstName;
        EditText lastName;
        EditText birthDate;
        EditText guardianName;
        EditText guardianPhone;
        CheckBox parentalAuthorization;
    }

    private final class StartupRingView extends View {
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int progress = 0;

        StartupRingView() {
            super(MainActivity.this);
            trackPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setStrokeWidth(dp(9));
            trackPaint.setStrokeCap(Paint.Cap.ROUND);
            trackPaint.setColor(Color.argb(48, 157, 255, 53));

            glowPaint.setStyle(Paint.Style.STROKE);
            glowPaint.setStrokeWidth(dp(20));
            glowPaint.setStrokeCap(Paint.Cap.ROUND);
            glowPaint.setColor(Color.argb(38, 157, 255, 53));

            progressPaint.setStyle(Paint.Style.STROKE);
            progressPaint.setStrokeWidth(dp(9));
            progressPaint.setStrokeCap(Paint.Cap.ROUND);
            progressPaint.setColor(LIME);
        }

        void setProgressValue(int value) {
            progress = Math.max(0, Math.min(100, value));
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float inset = dp(18);
            RectF oval = new RectF(inset, inset, getWidth() - inset, getHeight() - inset);
            canvas.drawArc(oval, -90f, 360f, false, trackPaint);
            if (progress > 0) {
                float sweep = 360f * progress / 100f;
                glowPaint.setColor(Color.argb(38, progress < 76 ? 214 : 255, progress < 76 ? 255 : 146, progress < 76 ? 63 : 43));
                progressPaint.setColor(progress < 76 ? LIME : ORANGE);
                canvas.drawArc(oval, -90f, sweep, false, glowPaint);
                canvas.drawArc(oval, -90f, sweep, false, progressPaint);
            }
        }
    }
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService prefetchIo = Executors.newFixedThreadPool(2);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SessionStore store;
    private LocalCache diskCache;
    private volatile boolean foregroundSyncBusy = false;
    private volatile boolean firstWarmupDone = false;
    private volatile boolean backgroundFullSyncRunning = false;
    private JSONObject snapshot;
    private volatile JSONArray studioRuntimeMenu = new JSONArray();
    private LinearLayout content, nav;
    private TextView syncStatus, title, syncIndicator, modeSwitch;
    private boolean adminMode = false;
    private String playerSectionBeforeAdmin = "Accueil";
    private ProgressBar startupProgress;
    private TextView startupProgressText;
    private LinearLayout syncBanner;
    private boolean startupLoading = false;
    private volatile boolean fullSyncIncomplete = false;
    private long startupStartedAt = 0L;
    private boolean startupCompletionScheduled = false;
    private boolean startupSoundEnabled = true;
    private StartupRingView startupRing;
    private TextView startupCenterProgressText;
    private TextView startupCenterStatus;
    private OnBackInvokedCallback systemBackCallback;
    private boolean adminSubpage = false;
    private final ArrayDeque<String> sectionHistory = new ArrayDeque<>();
    private String lastRenderedSection = "Accueil";
    private boolean restoringHistory = false;
    private String section = "Accueil";
    private String activeChatChannel = "";
    private String pendingDirectUserId = "";
    private String pendingDirectName = "";
    private String activeChatLabel = "";
    private String activeChatSignature = "";
    private String activeChatDraft = "";
    private EditText activeChatInput;
    private String pendingAccountingCsv = "";
    private String pendingPrivacyJson = "";
    private byte[] pendingAccountingDocumentBytes = null;
    private String pendingAccountingDocumentName = "document-comptable";
    private String pendingAccountingDocumentMime = "application/octet-stream";
    private String pendingAdminBackupJson = "";
    private String pendingMergeSource = "";
    private String pendingMergeTarget = "";
    private int pendingMergeConflictCount = 0;
    private int pendingMergeChangeCount = 0;
    private String galleryMode = "Pour toi";
    private String notificationScope = "Moi";
    private String notificationPriority = "Toutes";
    private boolean notificationUnreadOnly = false;
    private String notificationQuery = "";
    private String pendingOpenSessionDate = "";
    private String pendingOpenSessionTime = "";
    private String pendingOpenSessionTitle = "";
    private static final long CHAT_SYNC_MS = 2500L;
    private static final long LIVE_SYNC_MS = 2000L;
    private static final long NOTIFICATION_SYNC_MS = 5000L;
    private static final long ACTIVE_SYNC_MS = 10000L;
    private static final long NORMAL_SYNC_MS = 30000L;
    private static final long PRESENCE_SYNC_MS = 60000L;
    private static final long FOREGROUND_NOTIFICATION_CHECK_MS = 15000L;
    private static final long SNAPSHOT_RESUME_REFRESH_MS = 45000L;
    private static final long SYNC_BACKOFF_MAX_MS = 120000L;
    private long lastPresencePingAt = 0L;
    private long lastForegroundNotificationCheckAt = 0L;
    private volatile boolean notificationCheckBusy = false;
    private volatile int foregroundSyncFailures = 0;
    private volatile long lastSnapshotRefreshAt = 0L;
    private volatile long lastThemeSyncAt = 0L;
    private volatile boolean themeSyncBusy = false;
    private final Runnable chatSync = new Runnable(){ @Override public void run(){ if(!activeChatChannel.isEmpty() && "Messages".equals(section)){ loadActiveChannel(false); handler.postDelayed(this,CHAT_SYNC_MS); } } };
    // Synchronisation adaptative : rapide pour le live/notifications, plus calme ailleurs pour préserver batterie et serveur.
    private final Runnable autoSync = new Runnable(){ @Override public void run(){
        if(store==null||store.token().isEmpty())return;
        long now=System.currentTimeMillis();
        if(now-lastPresencePingAt>=PRESENCE_SYNC_MS){lastPresencePingAt=now;pingPresence();}
        if(now-lastForegroundNotificationCheckAt>=FOREGROUND_NOTIFICATION_CHECK_MS){lastForegroundNotificationCheckAt=now;checkNativeNotificationsAsync();}
        syncActiveSectionFast();
        handler.postDelayed(this, foregroundSyncIntervalMs());
    } };

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String before = section;
        applyNotificationIntent(intent);
        if (!section.equals(before) || (intent != null && intent.hasExtra("target_url"))) {
            render();
        }
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUiPrefs.migrateToSiteThemeSync(this);
        SiteThemeManager.applyCached(this);
        applyUiPalette();
        AppUiPrefs.applyWindow(this);
        store = new SessionStore(this);
        BackgroundSyncScheduler.ensureScheduled(this);
        diskCache = new LocalCache(this);
        // Le cache est persistant entre les ouvertures. On limite seulement les anciens médias
        // afin d'éviter qu'il grossisse indéfiniment sans sacrifier le fonctionnement hors ligne.
        prefetchIo.execute(() -> diskCache.maintain(250L * 1024L * 1024L, 30L * 24L * 60L * 60L * 1000L));
        if (store.token().isEmpty()) { auth(); return; }
        NativeNotifications.init(this);
        FirebasePushManager.ensureRegistered(this);
        buildUi();
        loadPublishedStudioRuntime();
        applyNotificationIntent(getIntent());
        String cached = store.cache();
        if (!cached.isEmpty()) {
            try { snapshot = new JSONObject(cached); } catch (Exception ignored) { snapshot = null; }
        }
        // Cache-first : l’accueil s’ouvre rapidement avec la dernière copie locale.
        // La synchronisation complète démarre immédiatement en arrière-plan sans bloquer l’utilisateur.
        showStartupLoading();
        startStartupMusic();
        registerSystemBackHandler();
        startWarmupAndRefresh();
    }

    private void applyUiPalette() {
        BG = AppUiPrefs.bg(this);
        CARD = AppUiPrefs.card(this);
        CARD_2 = AppUiPrefs.card2(this);
        LIME = AppUiPrefs.primary(this);
        PRIMARY_TEXT = AppUiPrefs.primaryText(this);
        ORANGE = AppUiPrefs.orange(this);
        ORANGE_DARK = AppUiPrefs.orangeDark(this);
        TEXT = AppUiPrefs.text(this);
        MUTED = AppUiPrefs.muted(this);
        STROKE = AppUiPrefs.stroke(this);
    }

    private String tr(String value) { return AppUiPrefs.t(this, value); }

    private void rebuildUiForPreferenceChange() {
        applyUiPalette();
        AppUiPrefs.applyWindow(this);
        buildUi();
        section = "Paramètres";
        lastRenderedSection = "Paramètres";
        if (snapshot != null) renderSettings();
        else render();
    }

    private void showLanguageDialog() {
        final String[] labels={"Français","English","Deutsch"};
        final String[] values={"fr","en","de"};
        int checked="en".equals(AppUiPrefs.language(this))?1:"de".equals(AppUiPrefs.language(this))?2:0;
        new AlertDialog.Builder(this)
                .setTitle(tr("Changer la langue"))
                .setSingleChoiceItems(labels, checked, (d,which)->{
                    AppUiPrefs.setLanguage(this, values[which]);
                    d.dismiss();
                    rebuildUiForPreferenceChange();
                })
                .setNegativeButton(tr("Annuler"), null)
                .show();
    }

    private void showThemeDialog() {
        final String[] labels={"Site synchronisé",tr("Sombre")+" (local)",tr("Clair")+" (local)"};
        String current=AppUiPrefs.theme(this);
        int checked="dark".equals(current)?1:"light".equals(current)?2:0;
        new AlertDialog.Builder(this)
                .setTitle(tr("Changer le thème"))
                .setSingleChoiceItems(labels, checked, (d,which)->{
                    AppUiPrefs.setTheme(this, which==1?"dark":which==2?"light":"site");
                    if(which==0) SiteThemeManager.applyCached(this);
                    d.dismiss();
                    rebuildUiForPreferenceChange();
                })
                .setNegativeButton(tr("Annuler"), null)
                .show();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // Coquille visuelle calée sur le site mobile : hamburger, logo centré, avatar.
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(3), dp(12), dp(3));
        top.setBackground(makeBg(BG, 0, 0));

        Button menu = iconButton("☰");
        menu.setTextColor(LIME);
        menu.setTextSize(25);
        menu.setBackground(makeBg(BG,12,STROKE));
        menu.setContentDescription("Ouvrir le menu");
        menu.setOnClickListener(v -> showMainMenu());
        top.addView(menu, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout logoWrap = new LinearLayout(this);
        logoWrap.setGravity(Gravity.CENTER);
        ImageView logo = new ImageView(this);
        logo.setImageResource(com.unitepaintball.app.R.drawable.unite_paintball_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setAdjustViewBounds(true);
        logo.setPadding(dp(2), dp(2), dp(2), dp(2));
        logoWrap.addView(logo, new LinearLayout.LayoutParams(dp(52), dp(52)));
        top.addView(logoWrap, new LinearLayout.LayoutParams(0, dp(56), 1));

        // 2.2.35 : header volontairement minimal comme la version historique appréciée.
        // Messages, notifications et profil sont dans la barre juste dessous : pas de doublon en haut.
        View rightSpacer = new View(this);
        top.addView(rightSpacer, new LinearLayout.LayoutParams(dp(44), dp(44)));
        root.addView(top);

        // 2.2.32 : bascule claire Joueur/Admin. Cachée tant que le compte n'est pas identifié comme administrateur.
        modeSwitch = tv("♙  MODE JOUEUR", 11, true, NeonPalette.CYAN);
        modeSwitch.setGravity(Gravity.CENTER);
        modeSwitch.setPadding(dp(10), dp(4), dp(10), dp(4));
        modeSwitch.setBackground(makeBg(CARD_2, 0, STROKE));
        modeSwitch.setVisibility(View.GONE);
        modeSwitch.setOnClickListener(v -> toggleAdminMode());
        root.addView(modeSwitch, new LinearLayout.LayoutParams(-1, dp(30)));

        // Navigation principale à 6 destinations : lisible sans masquer Messages ni les fonctions clés.
        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(0,0,0,0);
        nav.setBackground(makeBg(BG,0,STROKE));
        rebuildBottomNav();
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(60)));

        // Bandeau de synchronisation : le contenu en cache reste utilisable pendant le téléchargement.
        syncBanner = new LinearLayout(this);
        syncBanner.setOrientation(LinearLayout.VERTICAL);
        syncBanner.setPadding(dp(14),dp(7),dp(14),dp(7));
        syncBanner.setBackgroundColor(CARD);
        startupProgressText=tv("Synchronisation…",11,true,LIME);
        syncBanner.addView(startupProgressText);
        startupProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        startupProgress.setMax(100);startupProgress.setProgress(0);
        startupProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(LIME));
        LinearLayout.LayoutParams sbp=new LinearLayout.LayoutParams(-1,dp(6));sbp.setMargins(0,dp(5),0,0);syncBanner.addView(startupProgress,sbp);
        syncBanner.setVisibility(View.GONE);
        root.addView(syncBanner,new LinearLayout.LayoutParams(-1,-2));

        // État de synchronisation visible : discret, mais permet de savoir si les données locales sont à jour.
        syncIndicator = tv("Préparation de la synchronisation…", 10, true, MUTED);
        syncIndicator.setGravity(Gravity.CENTER_VERTICAL);
        syncIndicator.setPadding(dp(12), dp(3), dp(12), dp(3));
        syncIndicator.setBackgroundColor(CARD_2);
        syncIndicator.setOnClickListener(v -> {
            int pending = SyncApi.pendingCount(this) + MediaSyncQueue.pendingCount(this);
            int blocked = SyncApi.blockedCount(this) + MediaSyncQueue.blockedCount(this);
            long last = store==null?0L:store.lastSuccessSync();
            String when = last>0?new SimpleDateFormat("HH:mm",Locale.FRANCE).format(new Date(last)):"jamais";
            StringBuilder msg=new StringBuilder();
            msg.append(hasInternetConnection()?"Connexion disponible":"Hors ligne").append(" · dernière synchro ").append(when).append(". ");
            if(pending>0)msg.append(pending).append(" élément(s) conservé(s) sur ce téléphone et en attente d’envoi. ");
            else msg.append("Aucun élément en attente d’envoi. ");
            if(blocked>0)msg.append(blocked).append(" élément(s) nécessitent une vérification car le serveur les a refusés.");
            if(blocked>0){
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Synchronisation à vérifier")
                        .setMessage(msg.toString().trim()+"\n\nTu peux relancer les éléments refusés après avoir corrigé le problème côté site ou données.")
                        .setNegativeButton("Fermer", null)
                        .setPositiveButton("Réessayer", (d,w) -> retryBlockedSyncItems())
                        .show();
            }else if(pending>0 && hasInternetConnection()){
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Synchronisation en attente")
                        .setMessage(msg.toString().trim())
                        .setNegativeButton("Plus tard", null)
                        .setPositiveButton("Envoyer maintenant", (d,w) -> flushPendingNow())
                        .show();
            }else{
                Toast.makeText(this, msg.toString().trim(), Toast.LENGTH_LONG).show();
            }
            updateSyncIndicator();
        });
        root.addView(syncIndicator, new LinearLayout.LayoutParams(-1, dp(24)));

        // Conservés pour la logique interne.
        syncStatus = tv("", 1, false, Color.TRANSPARENT);
        title = tv("", 1, false, Color.TRANSPARENT);

        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(6), dp(12), dp(104));
        sc.setClipToPadding(false);
        sc.addView(content);
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        AppUiPrefs.applySafeArea(root);
        updateNavSelection();
        updateSyncIndicator();
    }

    private void rebuildBottomNav() {
        if (nav == null) return;
        nav.removeAllViews();
        if (adminMode) {
            addBottomNav(R.drawable.ic_nav_home, "Admin", "Dashboard");
            addBottomNav(R.drawable.ic_nav_bell, "Validations admin", "À valider");
            addBottomNav(R.drawable.ic_nav_gamepad, "Terrain admin", "Terrain");
            addBottomNav(R.drawable.ic_nav_chat, "Messages", "Messages");
            addBottomNav(R.drawable.ic_nav_users, "Joueurs admin", "Joueurs");
            addBottomNav(R.drawable.ic_nav_person, "Gestion admin", "Gestion");
        } else {
            // 2.2.35 : barre joueur remasterisée sur la version appréciée :
            // Accueil · Actualités · Amis · Jouer · Messages · Notifications · Profil.
            addCompactPlayerNav(R.drawable.ic_nav_home, "Accueil", "Accueil", "Accueil");
            addCompactPlayerNav(R.drawable.ic_nav_gallery, "Actualités", "Actualités", "Actus");
            addCompactPlayerNav(R.drawable.ic_nav_users, "Amis", "Amis", "Amis");
            addCompactPlayerNav(R.drawable.ic_nav_gamepad, "Jouer", "Jouer", "Jouer");
            addCompactPlayerNav(R.drawable.ic_nav_chat, "Messages", "Messages", "Messages");
            addCompactPlayerNav(R.drawable.ic_nav_bell, "Notifications", "Notifications", "Notifs");
            addCompactPlayerNav(R.drawable.ic_nav_person, "Profil", "Profil", "Profil");
        }
        updateNavSelection();
    }

    private void addCompactPlayerNav(int drawableRes, String target, String description, String shortLabel) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setTag(target);
        item.setContentDescription(description);
        item.setPadding(dp(1),dp(4),dp(1),0);

        ImageView i = new ImageView(this);
        i.setImageResource(drawableRes);
        i.setScaleType(ImageView.ScaleType.FIT_CENTER);
        i.setColorFilter(Color.rgb(155,166,162));
        i.setTag("icon");
        item.addView(i, new LinearLayout.LayoutParams(dp(22), dp(22)));

        // Libellé visible : l'icône seule n'indiquait pas la destination.
        TextView l = tv(shortLabel, 9, true, Color.rgb(155,166,162));
        l.setGravity(Gravity.CENTER);
        l.setSingleLine(true);
        l.setTag("label");
        LinearLayout.LayoutParams llp=new LinearLayout.LayoutParams(-1,dp(12));
        llp.setMargins(0,dp(1),0,0);
        item.addView(l,llp);

        View underline = new View(this);
        underline.setTag("underline");
        underline.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams ulp=new LinearLayout.LayoutParams(dp(28),dp(2));
        ulp.setMargins(0,dp(2),0,0);
        item.addView(underline,ulp);
        item.setOnClickListener(v -> { section = target; render(); });
        nav.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private void addBottomNav(int drawableRes, String target, String label) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setTag(target);
        item.setContentDescription(label);
        item.setPadding(dp(2),dp(7),dp(2),0);

        ImageView i = new ImageView(this);
        i.setImageResource(drawableRes);
        i.setScaleType(ImageView.ScaleType.FIT_CENTER);
        i.setColorFilter(Color.rgb(155,166,162));
        i.setTag("icon");
        item.addView(i, new LinearLayout.LayoutParams(dp(25), dp(25)));

        TextView l = tv(label, 8, true, Color.rgb(155,166,162));
        l.setGravity(Gravity.CENTER);
        l.setSingleLine(true);
        l.setTag("label");
        LinearLayout.LayoutParams llp=new LinearLayout.LayoutParams(-1,dp(23));
        llp.setMargins(0,dp(2),0,0);
        item.addView(l,llp);

        View underline = new View(this);
        underline.setTag("underline");
        underline.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams ulp=new LinearLayout.LayoutParams(dp(28),dp(3));
        ulp.setMargins(0,dp(2),0,0);
        item.addView(underline,ulp);
        item.setOnClickListener(v -> { section = target; render(); });
        nav.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private boolean hasAdminUiAccess() {
        if (snapshot == null) return false;
        if (isOwnerAccount(snapshot)) return true;
        if (snapshot.optBoolean("administrator", false) || snapshot.optBoolean("admin", false)) return true;
        JSONObject u=snapshot.optJSONObject("user");
        if (u == null) return false;
        if (u.optBoolean("administrator", false) || u.optBoolean("admin", false)) return true;
        String role=u.optString("role", u.optString("accountRole", "")).toLowerCase(Locale.FRANCE);
        return role.contains("admin") || role.contains("organizer") || role.contains("owner");
    }

    private void updateModeSwitch() {
        if (modeSwitch == null) return;
        boolean allowed=hasAdminUiAccess();
        modeSwitch.setVisibility(allowed ? View.VISIBLE : View.GONE);
        if (!allowed && adminMode) {
            adminMode=false;
            rebuildBottomNav();
        }
        if (!allowed) return;
        modeSwitch.setText(adminMode ? "⚙  MODE ADMIN  ·  revenir à l'espace joueur" : "♙  MODE JOUEUR  ·  ouvrir l'administration");
        modeSwitch.setTextColor(adminMode ? NeonPalette.CYAN_BRIGHT : NeonPalette.GREEN);
        modeSwitch.setBackground(makeBg(CARD_2, 0, adminMode ? NeonPalette.CYAN : NeonPalette.GREEN));
    }

    private void toggleAdminMode() {
        if (!hasAdminUiAccess()) {
            Toast.makeText(this, "Ce compte n'a pas accès au mode administrateur.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!adminMode) {
            playerSectionBeforeAdmin=section;
            adminMode=true;
            section="Admin";
        } else {
            adminMode=false;
            section=(playerSectionBeforeAdmin==null||playerSectionBeforeAdmin.isEmpty())?"Accueil":playerSectionBeforeAdmin;
        }
        rebuildBottomNav();
        updateModeSwitch();
        render();
    }

    private void updateNavSelection() {
        if (nav == null) return;
        for (int n=0;n<nav.getChildCount();n++) {
            View child=nav.getChildAt(n);
            if (!(child instanceof LinearLayout)) continue;
            LinearLayout item=(LinearLayout)child;
            boolean active=isNavTargetActive(String.valueOf(item.getTag()));
            for(int j=0;j<item.getChildCount();j++) {
                View v=item.getChildAt(j);
                if(v instanceof ImageView) ((ImageView)v).setColorFilter(active ? NeonPalette.CYAN_BRIGHT : Color.rgb(155,166,162));
                if(v instanceof TextView) ((TextView)v).setTextColor(active ? NeonPalette.CYAN_BRIGHT : Color.rgb(155,166,162));
                if("underline".equals(v.getTag())) v.setBackgroundColor(active ? NeonPalette.CYAN : Color.TRANSPARENT);
            }
        }
    }


    private boolean isNavTargetActive(String target){
        if(target==null)return false;
        if(target.equals(section))return true;
        if(adminMode){
            if("Admin".equals(target))return "Admin".equals(section);
            if("Validations admin".equals(target))return "Validations admin".equals(section);
            if("Terrain admin".equals(target))return "Terrain admin".equals(section)||"Live".equals(section)||"Stocks".equals(section)||"Incidents".equals(section);
            if("Joueurs admin".equals(target))return "Joueurs admin".equals(section);
            if("Gestion admin".equals(target))return "Gestion admin".equals(section)||"Finance admin".equals(section);
            return false;
        }
        if("Actualités".equals(target))return java.util.Arrays.asList("Actualités","Galerie","Événements").contains(section);
        if("Amis".equals(target))return java.util.Arrays.asList("Amis","Groupes").contains(section);
        if("Jouer".equals(target))return java.util.Arrays.asList("Jouer","Réservations","Nouvelle réservation","Espace joueur","QR","Live","Parties ouvertes","Avant de venir","Tournois","Scénarios","Arena").contains(section);
        if("Messages".equals(target))return "Messages".equals(section);
        if("Notifications".equals(target))return "Notifications".equals(section);
        if("Profil".equals(target))return java.util.Arrays.asList("Profil","Paramètres","Aide","Documents","Paiements","Adhésions","Attente","Points").contains(section);
        return false;
    }

    /** 2.2.36 : le site est la source de vérité des couleurs. */
    private void syncSiteThemeAsync(boolean force) {
        if (store == null || !AppUiPrefs.followsSiteTheme(this) || themeSyncBusy) return;
        long now=System.currentTimeMillis();
        if(!force && now-lastThemeSyncAt<5L*60L*1000L)return;
        themeSyncBusy=true;
        prefetchIo.execute(() -> {
            try {
                boolean changed=SiteThemeManager.syncNow(this, store.token());
                lastThemeSyncAt=System.currentTimeMillis();
                if(changed) runOnUiThread(() -> {
                    applyUiPalette();
                    AppUiPrefs.applyWindow(this);
                    buildUi();
                    if(snapshot!=null)render();
                });
            } catch (Exception ignored) {
                // Hors ligne ou endpoint pas encore déployé : conserver le dernier thème en cache.
            } finally { themeSyncBusy=false; }
        });
    }

    private void loadPublishedStudioRuntime() {
        if (store == null || store.token().isEmpty()) return;
        prefetchIo.execute(() -> {
            try {
                JSONObject result = ApiClient.get("/api/studio-runtime?target=application", store.token());
                JSONArray menu = result.optJSONArray("menu");
                if (result.optBoolean("published", false) && menu != null) studioRuntimeMenu = menu;
                else studioRuntimeMenu = new JSONArray();
            } catch (Exception ignored) { }
        });
    }

    private String studioSectionForDestination(String destination) {
        String raw = destination == null ? "" : destination.trim();
        if (raw.startsWith("/")) {
            if ("/".equals(raw)) return "Accueil";
            if (raw.startsWith("/reservations")) return "Réservations";
            if (raw.startsWith("/qr")) return "QR";
            if (raw.startsWith("/communication") || raw.startsWith("/messages")) return "Messages";
            if (raw.startsWith("/notifications")) return "Notifications";
            if (raw.startsWith("/galerie")) return "Galerie";
            if (raw.startsWith("/groupes")) return "Groupes";
            if (raw.startsWith("/adhesions")) return "Adhésions";
            if (raw.startsWith("/parties")) return "Live";
            if (raw.startsWith("/evenements")) return "Événements";
            if (raw.startsWith("/jeux")) return "Scénarios";
            if (raw.startsWith("/charte")) return "Sécurité";
            if (raw.startsWith("/profil")) return "Profil";
            if (raw.startsWith("/parametres")) return "Paramètres";
            if (raw.startsWith("/aide")) return "Aide";
            if (raw.startsWith("/assistant")) return "Assistant IA";
            if (raw.startsWith("/admin")) return "Admin";
        }
        return raw;
    }

    private String[][] applyStudioMenu(String[][] base) {
        String[][] out = new String[base.length][];
        for (int i=0;i<base.length;i++) out[i]=base[i].clone();
        JSONArray published = studioRuntimeMenu;
        if (published == null) return out;
        for (int i=0;i<published.length();i++) {
            JSONObject item=published.optJSONObject(i); if(item==null)continue;
            String target=studioSectionForDestination(item.optString("destination",""));
            if(target.isEmpty())continue;
            for(String[] row:out) {
                if(row.length<3 || !row[2].equalsIgnoreCase(target))continue;
                String label=item.optString("label","").trim(), detail=item.optString("detail","").trim();
                if(!label.isEmpty())row[0]=label;
                if(!detail.isEmpty())row[1]=detail;
            }
        }
        return out;
    }

    private void showMainMenu() {
        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BG);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(22),dp(22),dp(22),dp(16));
        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.unite_paintball_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER);
        head.addView(logo,new LinearLayout.LayoutParams(dp(132),dp(66)));
        TextView h=tv("Menu principal",24,true,TEXT); h.setPadding(0,dp(8),0,0); head.addView(h);
        TextView hs=tv("Choisis une catégorie ou recherche directement un outil",13,false,MUTED); head.addView(hs);
        EditText menuSearch=editField("Rechercher dans le menu…","");
        menuSearch.setSingleLine(true);
        menuSearch.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_search,0,0,0);
        menuSearch.setCompoundDrawablePadding(dp(8));
        LinearLayout.LayoutParams msp=new LinearLayout.LayoutParams(-1,dp(54));msp.setMargins(0,dp(14),0,0);head.addView(menuSearch,msp);
        shell.addView(head);

        View sep=new View(this);sep.setBackgroundColor(STROKE);shell.addView(sep,new LinearLayout.LayoutParams(-1,dp(1)));
        ScrollView sv=new ScrollView(this);
        LinearLayout searchShell=new LinearLayout(this);searchShell.setOrientation(LinearLayout.VERTICAL);searchShell.setPadding(dp(14),dp(12),dp(14),dp(22));
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout searchResults=new LinearLayout(this);searchResults.setOrientation(LinearLayout.VERTICAL);searchResults.setVisibility(View.GONE);
        searchShell.addView(list);searchShell.addView(searchResults);

        Button allFeatures=secondaryButton("Tous les outils · Site et application");allFeatures.setOnClickListener(v->{dialog.dismiss();openSharedFeature("/fonctionnalites");});list.addView(allFeatures);
        final ArrayList<LinearLayout> panels = new ArrayList<>();
        final ArrayList<TextView> arrows = new ArrayList<>();

        addMainMenuCategory(list, panels, arrows, "Navigation", "Les 5 espaces principaux", "⌂", applyStudioMenu(new String[][]{
                {"Accueil","Résumé, prochaine action et actualités","Accueil","⌂"},
                {"Actualités & photos","Fil communautaire, photos et événements","Actualités","▧"},
                {"Jouer","Réserver, QR, Live et préparation","Jouer","▶"},
                {"Arena Unité","Duels, grades et Arsenal","Arena","⚔"},
                {"Récompenses","XP, roulette, boutique et saison","Points","★"},
                {"Profil","Identité, statistiques et préférences","Profil","♙"}
        }), dialog);

        addMainMenuCategory(list, panels, arrows, "Ma partie", "Tout ce qui concerne ta venue", "▣", applyStudioMenu(new String[][]{
                {"Réservations","Dates, formules et participants","Réservations","▣"},
                {"Journées ouvertes","Rejoindre une partie déjà ouverte","Parties ouvertes","♟"},
                {"Mon QR code","Arrivée et présence","QR","⌁"},
                {"Paiements","Montants et reste à payer","Paiements","€"},
                {"Avant de venir","Checklist et préparation","Avant de venir","✓"},
                {"Documents","Chartes et autorisations","Documents","▤"},
                {"Liste d’attente","Places qui se libèrent","Attente","⌛"},
                {"Adhésions","Adhésion et avantages","Adhésions","★"}
        }), dialog);

        addMainMenuCategory(list, panels, arrows, "Communauté", "Messages, équipes et contenus", "◯", applyStudioMenu(new String[][]{
                {"Messagerie","Conversations privées et groupes","Messages","◯"},
                {"Groupes & équipes","Invitations et coéquipiers","Groupes","♛"},
                {"Mes amis","Contacts joueurs","Amis","♙"},
                {"Galerie","Photos, vidéos et actualités","Galerie","▧"},
                {"Événements","Prochaines parties et annonces","Événements","♟"}
        }), dialog);

        addMainMenuCategory(list, panels, arrows, "Plus", "Compétition, scénarios et assistance", "＋", applyStudioMenu(new String[][]{
                {"Tournois","Compétitions et inscriptions","Tournois","♛"},
                {"Scénarios","Missions paintball","Scénarios","⌖"},
                {"Sécurité","Règlement et consignes","Sécurité","✓"},
                {"Assistant IA","Aide intelligente Unité Paintball","Assistant IA","✦"},
                {"Paramètres","Compte, confidentialité et application","Paramètres","⚙"},
                {"Aide","FAQ et support","Aide","?"}
        }), dialog);

        if(hasAdminUiAccess()) addAdminMenuCategory(list, panels, arrows, dialog);

        menuSearch.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence x,int st,int c,int a){}
            public void onTextChanged(CharSequence x,int st,int before,int count){
                String q=x==null?"":x.toString().trim();
                boolean searching=!q.isEmpty();
                list.setVisibility(searching?View.GONE:View.VISIBLE);
                searchResults.setVisibility(searching?View.VISIBLE:View.GONE);
                if(searching)populateMenuSearch(searchResults,q,dialog);
            }
            public void afterTextChanged(Editable e){}
        });

        sv.addView(searchShell);shell.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        dialog.setContentView(shell);
        android.view.Window w=dialog.getWindow();
        if(w!=null){w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));w.setLayout((int)(getResources().getDisplayMetrics().widthPixels*.90f),-1);w.setGravity(Gravity.LEFT);}
        dialog.setOnShowListener(x->{android.view.Window ww=dialog.getWindow();if(ww!=null){ww.setLayout((int)(getResources().getDisplayMetrics().widthPixels*.90f),-1);ww.setGravity(Gravity.LEFT);}});
        dialog.show();
    }

    private void populateMenuSearch(LinearLayout out,String raw,android.app.Dialog dialog){
        out.removeAllViews();
        String q=raw.toLowerCase(Locale.FRANCE);
        String[][] regular=applyStudioMenu(new String[][]{
            {"Accueil / Actualités","Annonces, photos et informations","Accueil","⌂"},{"Fil d’actualité","Photos, événements et communauté","Actualités","▧"},{"Espace joueur","Mes informations essentielles","Espace joueur","♙"},{"Notifications","Alertes et actions","Notifications","●"},
            {"Réservations","Formules et dates","Réservations","▣"},{"Journées ouvertes","Parties à rejoindre avec places disponibles","Parties ouvertes","♟"},{"Paiements","Acomptes et reste à payer","Paiements","€"},{"Documents","Autorisations et justificatifs","Documents","▤"},{"Avant de venir","Checklist de préparation","Avant de venir","✓"},{"Mon QR code","Accès rapide","QR","⌁"},{"Liste d’attente","Places libérées","Attente","⌛"},{"Adhésions","Adhésion annuelle","Adhésions","★"},
            {"Mes amis","Contacts joueurs","Amis","♙"},{"Groupes et invitations","Équipes et groupes","Groupes","♛"},{"Messagerie","Conversations","Messages","◯"},{"Galerie","Photos et vidéos","Galerie","▧"},
            {"Parties / Live","Direct, équipes et résultats","Live","🎮"},{"Événements","Prochaines parties","Événements","♟"},{"Points et récompenses","Fidélité et classement","Points","★"},{"Arena Unité","Duels tactiques et grades","Arena","⚔"},{"Tournois","Compétitions","Tournois","♛"},{"Jeux et scénarios","Missions paintball","Scénarios","⌖"},{"Sécurité","Règlement et consignes","Sécurité","✓"},
            {"Mon profil & progression","Identité, progression et préférences","Profil","♙"},{"Assistant IA","Aide intelligente Unité Paintball","Assistant IA","✦"},{"Paramètres","Compte et application","Paramètres","⚙"},{"Aide et assistance","FAQ et support","Aide","?"}
        });
        int found=0;
        for(String[] it:regular){String hay=(it[0]+" "+it[1]+" "+it[2]+" "+tr(it[0])+" "+tr(it[1])).toLowerCase(AppUiPrefs.locale(this));if(hay.contains(q.toLowerCase(AppUiPrefs.locale(this)))){out.addView(mainMenuLeaf(it[0],it[1],it[2],it[3],dialog));found++;}}
        String[][] admin={
            {"Administration · Tableau de bord","Vue générale","dashboard"},{"Administration · Scanner QR","Contrôle d’arrivée","scanner"},{"Administration · Live","Partie en direct","live"},{"Administration · Tournois","Gestion des tournois","tournaments"},{"Administration · Centre de validation","Actions en attente","validation"},{"Administration · Joueurs","Recherche et fiches","players"},{"Administration · Participations","Accepter ou refuser","participations"},{"Administration · Doubles comptes","Fusion sécurisée propriétaire","duplicates"},{"Administration · Réservations","Planning et gestion","bookings"},{"Administration · Confirmations","Rappels et confirmations","confirmations"},{"Administration · Communications","Messages et notifications","communications"},{"Administration · Caisse","Paiements terrain","payments"},{"Administration · Comptabilité","Recettes et dépenses","financeapp"},{"Administration · Stocks","Matériel et consommables","stockapp"},{"Administration · Incidents","Premiers secours et suivi","incidentapp"},{"Administration · Galerie","Publications et médias","media"},{"Administration · Statistiques","Fréquentation et résultats","stats"},{"Administration · Gamification","XP, missions, roulette et récompenses","gamification"},{"Administration · Paramètres","Réglages généraux","settings"},{"Administration · Studio","Pages et brouillons","studio"},{"Administration · Sauvegardes","D1 + médias","backups"}
        };
        if(hasAdminUiAccess()) for(String[] it:admin){String hay=(it[0]+" "+it[1]+" "+tr(it[0])+" "+tr(it[1])).toLowerCase(AppUiPrefs.locale(this));if(hay.contains(q.toLowerCase(AppUiPrefs.locale(this)))){LinearLayout row=adminMenuLeaf(it[0],it[1],it[2],dialog);out.addView(row);found++;}}
        if(found==0)out.addView(cardText("Aucun résultat","Essaie un mot comme réservation, QR, paiement, tournoi, message ou admin."));
    }

    private void addMainMenuCategory(LinearLayout list, ArrayList<LinearLayout> panels, ArrayList<TextView> arrows, String title, String subtitle, String icon, String[][] items, android.app.Dialog dialog){
        LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,-2);wp.setMargins(0,0,0,dp(10));wrap.setLayoutParams(wp);
        LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(12),dp(12),dp(12),dp(12));header.setBackground(makeBg(CARD,16,STROKE));
        TextView ico=tv(icon,21,true,LIME);ico.setGravity(Gravity.CENTER);ico.setBackground(makeBg(Color.argb(18,157,255,53),12,0));header.addView(ico,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,0,0);copy.addView(tv(title,16,true,TEXT));copy.addView(tv(subtitle,12,false,MUTED));header.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=tv("▼",16,true,LIME);arrow.setGravity(Gravity.CENTER);header.addView(arrow,new LinearLayout.LayoutParams(dp(32),dp(44)));wrap.addView(header);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(10),dp(8),0,0);panel.setVisibility(View.GONE);
        for(String[] item:items) panel.addView(mainMenuLeaf(item[0],item[1],item[2],item[3],dialog));
        wrap.addView(panel);list.addView(wrap);panels.add(panel);arrows.add(arrow);
        header.setOnClickListener(v->{boolean opening=panel.getVisibility()!=View.VISIBLE;for(int i=0;i<panels.size();i++){panels.get(i).setVisibility(View.GONE);arrows.get(i).setText("▼");}if(opening){panel.setVisibility(View.VISIBLE);arrow.setText("▲");}});
    }

    private LinearLayout mainMenuLeaf(String label,String detail,String target,String icon,android.app.Dialog dialog){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(9),dp(10),dp(9));row.setBackground(makeBg(CARD_2,14,STROKE));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(64));rp.setMargins(0,0,0,dp(7));row.setLayoutParams(rp);
        TextView ico=tv(icon,18,true,LIME);ico.setGravity(Gravity.CENTER);row.addView(ico,new LinearLayout.LayoutParams(dp(38),dp(38)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(10),0,0,0);copy.addView(tv(label,14,true,TEXT));TextView d=tv(detail,11,false,MUTED);d.setPadding(0,dp(2),0,0);copy.addView(d);row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        TextView go=tv("›",22,false,Color.argb(145,157,255,53));go.setGravity(Gravity.CENTER);row.addView(go,new LinearLayout.LayoutParams(dp(28),dp(40)));
        row.setOnClickListener(v->{section=target;dialog.dismiss();render();});return row;
    }

    private void addAdminMenuCategory(LinearLayout list, ArrayList<LinearLayout> panels, ArrayList<TextView> arrows, android.app.Dialog dialog){
        LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,-2);wp.setMargins(0,0,0,dp(10));wrap.setLayoutParams(wp);
        LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(12),dp(12),dp(12),dp(12));header.setBackground(makeBg(CARD_2,16,ORANGE));
        TextView ico=tv("⚙",21,true,ORANGE);ico.setGravity(Gravity.CENTER);header.addView(ico,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,0,0);copy.addView(tv("Administration",16,true,ORANGE));copy.addView(tv("Tous les outils administrateur rangés par catégorie",12,false,MUTED));header.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=tv("▼",16,true,ORANGE);arrow.setGravity(Gravity.CENTER);header.addView(arrow,new LinearLayout.LayoutParams(dp(32),dp(44)));wrap.addView(header);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(10),dp(8),0,0);panel.setVisibility(View.GONE);
        panel.addView(adminMenuLeaf("Tableau de bord","Vue générale et accès rapides","dashboard",dialog));
        panel.addView(adminSubmenu("Terrain",new String[][]{{"Scanner un QR","Contrôle d’arrivée et présence","scanner"},{"Partie en direct","Score et arbitrage LIVE","live"},{"Tournois","Création, inscriptions et tableaux","tournaments"}},dialog));
        panel.addView(adminSubmenu("À valider",new String[][]{{"Centre de validation","Toutes les actions en attente","validation"},{"Nouveaux arrivants","Valider ou refuser les comptes","newcomers"},{"Nouvelles réservations","Demandes de parties","bookings"},{"Modifications","Comparer avant et après","changes"},{"Adhésions","Nouvelles adhésions","memberships"}},dialog));
        panel.addView(adminSubmenu("Joueurs",new String[][]{{"Tous les joueurs","Recherche et fiches","players"},{"Participations","Accepter ou refuser les demandes de parties ouvertes","participations"},{"Doubles comptes","Prévisualiser et fusionner en sécurité","duplicates"},{"Absences et blocages","Compteurs, notes et réautorisation","blocked"}},dialog));
        panel.addView(adminSubmenu("Réservations",new String[][]{{"À venir","Planning et gestion","bookings"},{"Confirmations","À envoyer, en attente et reçues","confirmations"},{"Historique","Réservations terminées","bookings"}},dialog));
        panel.addView(adminSubmenu("Parties & tournois",new String[][]{{"Partie en cours","Score et arbitrage LIVE","live"},{"Sessions","Prochaines parties et historique","games"},{"Gestion des tournois","Création et inscriptions","tournaments"}},dialog));
        panel.addView(adminSubmenu("Communications",new String[][]{{"Centre de communication","Confirmations, rappels et historique","communications"},{"Messagerie","Conversations","communications"},{"Notifications","Push et préférences","notifications"}},dialog));
        panel.addView(adminSubmenu("Gestion",new String[][]{{"Bons cadeaux","Créer, partager et contrôler les bons avec QR","giftcertificates"},{"Caisse hors ligne","Brouillons puis synchronisation","payments"},{"Comptabilité","Recettes, dépenses et exports","financeapp"},{"Stocks","Mouvements et alertes","stockapp"},{"Matériel prêté","Attribution et restitution","stockapp"},{"Incidents / Premiers secours","Fiches et suivi","incidentapp"},{"Documents","Chartes et autorisations","documentsapp"},{"Galerie / Actualités","Photos, vidéos et publications","media"},{"Statistiques","Fréquentation et résultats","stats"}},dialog));
        panel.addView(adminSubmenu("Gamification",new String[][]{{"Centre Gamification","XP, niveaux, rangs, badges, missions, saisons et roulette","gamification"},{"Arena Unité","Duels, grades, accessoires et classement","arenaadmin"},{"Progression joueur","Vue identique au site","rewards"}},dialog));
        panel.addView(adminSubmenu("Paramètres",new String[][]{{"Paramètres généraux","Capacités et règles métier","settings"},{"Historique administrateur","Journal des actions","audit"}},dialog));
        panel.addView(adminSubmenu("Studio & sauvegardes",new String[][]{{"Studio partagé · App + Site","Même configuration publiée","studio"},{"Sauvegardes","Inventaire des données à protéger","backups"}},dialog));
        wrap.addView(panel);list.addView(wrap);panels.add(panel);arrows.add(arrow);
        header.setOnClickListener(v->{boolean opening=panel.getVisibility()!=View.VISIBLE;for(int i=0;i<panels.size();i++){panels.get(i).setVisibility(View.GONE);arrows.get(i).setText("▼");}if(opening){panel.setVisibility(View.VISIBLE);arrow.setText("▲");}});
    }

    private LinearLayout adminSubmenu(String title,String[][] items,android.app.Dialog dialog){
        LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,-2);wp.setMargins(0,0,0,dp(7));wrap.setLayoutParams(wp);
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(12),dp(10),dp(12),dp(10));head.setBackground(makeBg(CARD_2,13,STROKE));head.addView(tv(title,14,true,TEXT),new LinearLayout.LayoutParams(0,-2,1));TextView arrow=tv("＋",17,true,ORANGE);head.addView(arrow,new LinearLayout.LayoutParams(dp(30),dp(34)));wrap.addView(head);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(8),dp(6),0,0);body.setVisibility(View.GONE);for(String[] it:items)body.addView(adminMenuLeaf(it[0],it[1],it[2],dialog));wrap.addView(body);
        head.setOnClickListener(v->{boolean open=body.getVisibility()!=View.VISIBLE;body.setVisibility(open?View.VISIBLE:View.GONE);arrow.setText(open?"−":"＋");});return wrap;
    }

    private LinearLayout adminMenuLeaf(String label,String detail,String action,android.app.Dialog dialog){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(12),dp(10),dp(12),dp(10));row.setBackground(makeBg(CARD_2,12,STROKE));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.setMargins(0,0,0,dp(6));row.setLayoutParams(rp);row.addView(tv(label,14,true,TEXT));row.addView(tv(detail,11,false,MUTED));
        row.setOnClickListener(v->{dialog.dismiss();openAdminMenuAction(label,detail,action);});return row;
    }

    private void openSharedFeature(String path){startActivity(new Intent(this,SharedFeatureActivity.class).putExtra("path",path));}

    private void openAdminMenuAction(String label,String detail,String action){
        if(hasAdminUiAccess()){adminMode=true;rebuildBottomNav();updateModeSwitch();}
        if("dashboard".equals(action)){section="Admin";render();}
        else if("studio".equals(action))startActivity(new Intent(this,StudioActivity.class));
        else if("players".equals(action))loadAdminPlayers();
        else if("bookings".equals(action))loadAdminBookings();
        else if("live".equals(action))loadAdminLive();
        else if("scanner".equals(action))startActivity(new Intent(this,ScannerActivity.class));
        else if("giftcertificates".equals(action))startActivity(new Intent(this,GiftCertificateActivity.class));
        else if("notifications".equals(action)){section="Notifications";render();}
        else if("communications".equals(action)){section="Messages";render();}
        else if("payments".equals(action)){section="Finance admin";render();}
        else if("tournaments".equals(action)){section="Tournois";render();}
        else if("memberships".equals(action)){section="Adhésions";render();}
        else if("media".equals(action)){section="Galerie";render();}
        else if("rewards".equals(action)){section="Points";render();}
        else if("gamification".equals(action))loadAdminGeneric("Gamification","/api/admin-gamification","XP, rangs, missions, roulette et récompenses.");
        else if("arenaadmin".equals(action))loadAdminGeneric("Arena","/api/arena","Grades, équipements, matchmaking et activité Arena.");
        else if("financeapp".equals(action)){section="Finance admin";render();}
        else if("stockapp".equals(action)){section="Stocks";render();}
        else if("incidentapp".equals(action)){section="Incidents";render();}
        else if("documentsapp".equals(action)){section="Documents";render();}
        else if("validation".equals(action))loadAdminTasks();
        else if("participations".equals(action))loadAdminParticipations();
        else if("duplicates".equals(action))loadAdminAccountMerge();
        else if("backups".equals(action))loadAdminBackups();
        else if("confirmations".equals(action))loadAdminConfirmations();
        else if("changes".equals(action))loadAdminBookingChanges();
        else if("newcomers".equals(action))loadAdminMembers();
        else if("blocked".equals(action))loadAdminRestrictions();
        else if("settings".equals(action))loadAdminSettingsNative();
        else if("audit".equals(action))loadAdminAudit();
        else loadAdminGeneric(label,adminPath(action),detail);
    }

    private void refresh(boolean visible) {
        if (store.token().isEmpty()) return;
        syncSiteThemeAsync(false);
        if (visible) {
            syncStatus.setText("Synchronisation avec le site…");
            setStartupProgress(35, "Connexion sécurisée au site…");
        }
        io.execute(() -> {
            try {
                JSONObject r=buildExistingSiteSnapshot();
                runOnUiThread(() -> setStartupProgress(75, "Chargement de ton espace…"));
                snapshot=r; store.saveCache(r.toString());
                lastSnapshotRefreshAt=System.currentTimeMillis();
                store.saveLastSuccessSync(lastSnapshotRefreshAt);
                checkNativeNotifications();
                runOnUiThread(()->{
                    syncStatus.setText("À jour · "+new SimpleDateFormat("HH:mm",Locale.FRANCE).format(new Date()));
                    updateSyncIndicator();
                    finishStartupAndRender();
                });
            } catch (ApiClient.ApiException e) {
                if(e.status==401){runOnUiThread(this::auth);return;}
                runOnUiThread(()->handleStartupFailure(e.getMessage()));
            } catch (Exception e) { runOnUiThread(()->handleStartupFailure(e.getMessage())); }
        });
    }


    /** Construit le snapshot natif uniquement avec les API déjà présentes sur le site publié. */
    private JSONObject buildExistingSiteSnapshot() throws Exception {
        String session = store.token();
        JSONObject platform = ApiClient.get("/api/platform", session);
        JSONObject out = new JSONObject();

        JSONObject user = platform.optJSONObject("user");
        JSONObject profile = platform.optJSONObject("profile");
        JSONObject latest = platform.optJSONObject("latestBooking");
        JSONArray messages = platform.optJSONArray("messages");
        JSONArray notifications = platform.optJSONArray("notifications");
        JSONArray qrCodes = platform.optJSONArray("qrCodes");
        JSONArray bookingParticipants = platform.optJSONArray("bookingParticipants");

        out.put("user", user == null ? JSONObject.NULL : user);
        out.put("profile", profile == null ? JSONObject.NULL : profile);
        out.put("administrator", platform.optBoolean("administrator", false));
        out.put("next", latest == null ? JSONObject.NULL : latest);
        JSONArray upcoming = new JSONArray();
        if (latest != null) upcoming.put(latest);
        out.put("upcoming", upcoming);
        out.put("ownBooking", latest != null);
        out.put("participant", JSONObject.NULL);
        out.put("messageCount", messages == null ? 0 : messages.length());
        out.put("alerts", notifications == null ? new JSONArray() : notifications);
        out.put("qrCodes", qrCodes == null ? new JSONArray() : qrCodes);
        out.put("bookingParticipants", bookingParticipants == null ? new JSONArray() : bookingParticipants);

        try {
            JSONObject rewards = ApiClient.get("/api/rewards", session);
            out.put("points", rewards.optInt("points", profile == null ? 0 : profile.optInt("points", 0)));
        } catch (Exception ignored) {
            out.put("points", profile == null ? 0 : profile.optInt("points", 0));
        }

        try {
            JSONObject content = ApiClient.get("/api/content", session);
            out.put("comingEvents", content.optJSONArray("events") == null ? new JSONArray() : content.optJSONArray("events"));
        } catch (Exception ignored) {
            out.put("comingEvents", new JSONArray());
        }
        return out;
    }

    private void startWarmupAndRefresh(){
        if(firstWarmupDone)return;
        firstWarmupDone=true;
        setStartupProgress(12,"Lecture de la copie locale…");
        syncSiteThemeAsync(true);
        startCriticalStartupSync();
    }

    /**
     * 2.2.28 : l'écran d'accueil ne s'ouvre plus dès que le cache existe.
     * Les modules critiques du joueur sont d'abord interrogés réellement ; la barre centrale
     * reflète le nombre de modules vérifiés. Une 401 renvoie vers l'authentification ; les
     * modules optionnels interdits n'empêchent pas l'ouverture. En cas de réseau dégradé,
     * la dernière copie locale reste le filet de sécurité.
     */
    private static void validateStartupSnapshot(String endpoint,JSONObject data) throws Exception {
        if(data==null||data.has("error"))throw new java.io.IOException("Données serveur invalides.");
        if("/api/platform".equals(endpoint)&&data.optJSONObject("user")==null)throw new java.io.IOException("Plateforme incomplète.");
        if("/api/avatar/soldier".equals(endpoint)&&(data.optJSONObject("config")==null||data.optJSONArray("collection")==null))throw new java.io.IOException("Inventaire 3D incomplet.");
    }

    private void startCriticalStartupSync(){
        if(store==null||store.token().isEmpty()){auth();return;}

        // 2.2.53 : l’écran de chargement reste affiché jusqu’à la fin de la synchronisation
        // complète des données globales accessibles à ce compte. Les modules admin refusés
        // en 403/404 sont considérés comme "vérifiés" mais jamais comme des données reçues.
        final String[] endpoints={
                "/api/platform","/api/avatar/soldier","/api/avatar","/api/content","/api/rewards","/api/gamification?scope=profile","/api/arena","/api/communication",
                "/api/player-notifications","/api/friends","/api/group-events","/api/operations",
                "/api/live-game?scope=live","/api/management?admin=1","/api/personal-funds",
                "/api/member","/api/presence","/api/safety","/api/scenarios","/api/teams",
                "/api/sessions","/api/commercial","/api/admin-content","/api/admin-settings",
                "/api/admin-players","/api/members","/api/tournaments-admin","/api/support",
                "/api/information","/api/player-content","/api/feedback","/api/player-pricing",
                "/api/insurance-choice"
        };
        setStartupProgress(8,"Envoi des modifications en attente…");

        prefetchIo.execute(()->{
            try{
                // Avant de relire le serveur, pousser ce que le téléphone avait gardé hors ligne.
                SyncApi.flush(this,store.token(),100);
                MediaSyncQueue.flush(this,store.token(),30);
            }catch(Exception ignored){}

            final java.util.concurrent.atomic.AtomicInteger done=new java.util.concurrent.atomic.AtomicInteger(0);
            final java.util.concurrent.atomic.AtomicInteger ok=new java.util.concurrent.atomic.AtomicInteger(0);
            final java.util.concurrent.atomic.AtomicInteger networkFailures=new java.util.concurrent.atomic.AtomicInteger(0);
            final java.util.concurrent.atomic.AtomicBoolean unauthorized=new java.util.concurrent.atomic.AtomicBoolean(false);
            final java.util.concurrent.atomic.AtomicBoolean platformReady=new java.util.concurrent.atomic.AtomicBoolean(false);

            runOnUiThread(()->setStartupProgress(12,"Synchronisation complète · 0 / "+endpoints.length));
            for(final String ep:endpoints){
                prefetchIo.execute(()->{
                    try{
                        JSONObject d=ApiClient.get(ep,store.token());
                        validateStartupSnapshot(ep,d);
                        diskCache.putJson(ep,d);ok.incrementAndGet();
                        if("/api/platform".equals(ep)){mergePlatformSnapshot(d);platformReady.set(true);}
                        if("/api/avatar/soldier".equals(ep))getSharedPreferences("avatar-cache",MODE_PRIVATE).edit().putString("soldier-"+Integer.toHexString(store.token().hashCode()),d.toString()).apply();
                        if("/api/content".equals(ep)||"/api/platform".equals(ep)||"/api/communication".equals(ep))
                            diskCache.prefetchMedia(d,store.token(),24);
                        if("/api/communication".equals(ep))warmConversationChannels(d);
                    }catch(ApiClient.ApiException e){
                        if(e.status==401)unauthorized.set(true);
                        else if(StartupSyncPolicy.failure(ep,e.status))networkFailures.incrementAndGet();
                    }catch(Exception e){networkFailures.incrementAndGet();}

                    int n=done.incrementAndGet();
                    int percent=12+(int)Math.round((n*86.0)/endpoints.length);
                    runOnUiThread(()->setStartupProgress(percent,"Synchronisation complète · "+n+" / "+endpoints.length));
                    if(n==endpoints.length){
                        if(unauthorized.get()){runOnUiThread(this::auth);return;}
                        int pending=SyncApi.pendingCount(this)+MediaSyncQueue.pendingCount(this);
                        if(StartupSyncPolicy.ready(platformReady.get(),networkFailures.get(),pending)){
                            fullSyncIncomplete=false;foregroundSyncFailures=0;long now=System.currentTimeMillis();store.saveLastSuccessSync(now);
                            runOnUiThread(()->{
                                setStartupProgress(100,"Synchronisation terminée · "+ok.get()+" module(s) à jour");
                                finishStartupAndRender();
                            });
                        }else{
                            String why=pending>0
                                    ? pending+" élément(s) restent à envoyer."
                                    : "Certains modules n’ont pas pu être actualisés.";
                            runOnUiThread(()->handleStartupFailure(why));
                        }
                    }
                });
            }
        });
    }

    /**
     * Synchronise tous les modules GET globaux et cacheables sans bloquer l’interface. Les routes nécessitant un token, une date ou une clé sont chargées uniquement dans leur écran avec le bon contexte.
     * Les écrans continuent d’afficher leur cache immédiatement grâce à loadJson().
     */
    private void startFullBackgroundSync(){
        if(backgroundFullSyncRunning||store==null||store.token().isEmpty())return;
        backgroundFullSyncRunning=true;

        final String[] endpoints={
                "/api/platform","/api/avatar/soldier","/api/avatar","/api/content","/api/rewards","/api/gamification?scope=profile","/api/arena","/api/communication",
                "/api/player-notifications","/api/friends","/api/group-events","/api/operations",
                "/api/live-game?scope=live","/api/management?admin=1","/api/personal-funds",
                "/api/member","/api/presence","/api/safety","/api/scenarios","/api/teams",
                "/api/sessions","/api/commercial","/api/admin-content","/api/admin-settings",
                "/api/admin-players","/api/members","/api/tournaments-admin","/api/support",
                "/api/information","/api/player-content","/api/feedback","/api/player-pricing",
                "/api/insurance-choice"
        };

        runOnUiThread(()->{
            if(syncBanner!=null)syncBanner.setVisibility(View.VISIBLE);
            if(startupProgress!=null){startupProgress.setProgress(0);startupProgress.setMax(100);}
            if(startupProgressText!=null)startupProgressText.setText("Synchronisation en arrière-plan · 0 / "+endpoints.length);
            updateSyncIndicator();
        });

        final java.util.concurrent.atomic.AtomicInteger done=new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger failures=new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger ok=new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicBoolean unauthorized=new java.util.concurrent.atomic.AtomicBoolean(false);

        for(final String ep:endpoints){
            prefetchIo.execute(()->{
                try{
                    JSONObject d=ApiClient.get(ep,store.token());
                    diskCache.putJson(ep,d);
                    ok.incrementAndGet();
                    if("/api/platform".equals(ep)){mergePlatformSnapshot(d);}
                    if("/api/content".equals(ep)||"/api/platform".equals(ep)||"/api/communication".equals(ep))
                        diskCache.prefetchMedia(d,store.token(),24);
                    if("/api/communication".equals(ep))warmConversationChannels(d);
                }catch(ApiClient.ApiException e){
                    if(e.status==401)unauthorized.set(true);
                    else if(StartupSyncPolicy.failure(ep,e.status))failures.incrementAndGet();
                }catch(Exception ignored){failures.incrementAndGet();}

                int n=done.incrementAndGet();
                int percent=(int)Math.round((n*100.0)/endpoints.length);
                runOnUiThread(()->{
                    if(startupProgress!=null)startupProgress.setProgress(percent);
                    if(startupProgressText!=null)startupProgressText.setText("Synchronisation en arrière-plan · "+n+" / "+endpoints.length);
                });

                if(n==endpoints.length){
                    backgroundFullSyncRunning=false;
                    if(unauthorized.get()){runOnUiThread(this::auth);return;}
                    if(failures.get()>0||SyncApi.pendingCount(this)+MediaSyncQueue.pendingCount(this)>0){fullSyncIncomplete=true;runOnUiThread(()->{if(syncStatus!=null)syncStatus.setText("Synchronisation incomplète");if(startupProgressText!=null)startupProgressText.setText("Connexion ou envoi incomplet · réessayer");});return;}
                    fullSyncIncomplete=false;foregroundSyncFailures=0;long now=System.currentTimeMillis();
                    store.saveLastSuccessSync(now);
                    runOnUiThread(()->{
                        if(syncStatus!=null)syncStatus.setText("À jour · "+new SimpleDateFormat("HH:mm",Locale.FRANCE).format(new Date(now)));
                        updateSyncIndicator();
                        if(syncBanner!=null){
                            if(startupProgress!=null)startupProgress.setProgress(100);
                            if(startupProgressText!=null)startupProgressText.setText("Synchronisation terminée · "+ok.get()+" module(s) actualisé(s)");
                            handler.postDelayed(()->{if(syncBanner!=null)syncBanner.setVisibility(View.GONE);},1400L);
                        }
                        // Si l’utilisateur est toujours sur un écran affiché depuis le cache, on le rafraîchit une fois.
                        renderCurrentSectionFresh();
                    });
                }
            });
        }
    }

    private void warmConversationChannels(JSONObject communication){
        JSONArray channels=communication==null?null:communication.optJSONArray("channels");if(channels==null)return;
        int max=Math.min(channels.length(),40);
        for(int i=0;i<max;i++){
            JSONObject c=channels.optJSONObject(i);if(c==null)continue;String id=c.optString("id","");if(id.isEmpty())continue;
            try{
                String network="/api/communication?channel="+java.net.URLEncoder.encode(id,"UTF-8");
                JSONObject d=ApiClient.get(network,store.token());
                String cacheKey="/api/communication?channel="+android.net.Uri.encode(id);
                diskCache.putJson(cacheKey,d);diskCache.prefetchMedia(d,store.token(),20);
            }catch(Exception ignored){}
        }
    }

    private String activeEndpoint(){
        if("Messages".equals(section))return activeChatChannel.isEmpty()?"/api/communication":"/api/communication?channel="+android.net.Uri.encode(activeChatChannel);
        if("Notifications".equals(section))return "/api/player-notifications";
        if("Galerie".equals(section)||"Événements".equals(section))return "/api/content";
        if("Joueurs".equals(section)||"Amis".equals(section))return "/api/friends";
        if("Adhésions".equals(section))return "/api/member";
        if("Tournois".equals(section))return "/api/group-events";
        if("Live".equals(section))return "/api/live-game?scope=live";
        if("Points".equals(section))return "/api/gamification?scope=profile";
        if("Attente".equals(section))return "/api/operations";
        if("Admin".equals(section)||"Finance admin".equals(section))return "/api/management?admin=1";
        return "/api/platform";
    }

    private void pingPresence(){
        if(store==null||store.token().isEmpty())return;
        prefetchIo.execute(()->{try{ApiClient.get("/api/presence",store.token());}catch(Exception ignored){}});
    }

    private long foregroundSyncIntervalMs(){
        long base;
        if("Live".equals(section))base=LIVE_SYNC_MS;
        else if("Notifications".equals(section))base=NOTIFICATION_SYNC_MS;
        else if("Messages".equals(section)&&!activeChatChannel.isEmpty())base=CHAT_SYNC_MS;
        else if("Messages".equals(section)||"Réservations".equals(section)||"Adhésions".equals(section)||"Tournois".equals(section)||"Joueurs".equals(section)||"Amis".equals(section)||"Événements".equals(section)||"Admin".equals(section)||"Finance admin".equals(section)||"Stocks".equals(section)||"Incidents".equals(section))base=ACTIVE_SYNC_MS;
        else base=NORMAL_SYNC_MS;

        // En cas de panne réseau/serveur, ralentir progressivement au lieu de multiplier les requêtes.
        int step=Math.min(foregroundSyncFailures,3);
        long delay=base*(1L<<step);
        return Math.min(delay,SYNC_BACKOFF_MAX_MS);
    }

    private void syncActiveSectionFast(){
        if(foregroundSyncBusy||store==null||store.token().isEmpty())return;
        foregroundSyncBusy=true;
        final String ep=activeEndpoint();
        final String requestedSection=section;
        prefetchIo.execute(()->{
            try{
                int pushed=SyncApi.flush(this,store.token(),6);
                int pushedMedia=MediaSyncQueue.flush(this,store.token(),2);
                if(pushed+pushedMedia>0)runOnUiThread(()->syncStatus.setText("Envoi vers le site · "+(pushed+pushedMedia)+" élément(s) synchronisé(s)"));
                String before=diskCache.getJsonText(ep);JSONObject data=ApiClient.get(ep,store.token());foregroundSyncFailures=0;String after=data.toString();diskCache.putJson(ep,data);runOnUiThread(this::updateSyncIndicator);
                if("/api/content".equals(ep)||"/api/platform".equals(ep))diskCache.prefetchMedia(data,store.token(),6);
                if(!after.equals(before))runOnUiThread(()->{
                    // La réponse peut arriver après un changement d'écran. Le cache reste mis à jour,
                    // mais une ancienne requête ne doit jamais redessiner une nouvelle section.
                    if(!requestedSection.equals(section) || !ep.equals(activeEndpoint()))return;
                    syncStatus.setText("Synchro temps réel · "+new SimpleDateFormat("HH:mm:ss",Locale.FRANCE).format(new Date()));
                    if("Messages".equals(section)&&!activeChatChannel.isEmpty())loadActiveChannel(false);
                    else if("Messages".equals(section)||"Notifications".equals(section)||"Live".equals(section)||"Adhésions".equals(section)||"Tournois".equals(section)||"Joueurs".equals(section)||"Amis".equals(section)||"Événements".equals(section))renderCurrentSectionFresh();
                    else if("Finance admin".equals(section)&&!adminSubpage)render();
                    else if("/api/platform".equals(ep)){mergePlatformSnapshot(data);renderCurrentSectionFresh();}
                });
            }catch(Exception ignored){foregroundSyncFailures=Math.min(foregroundSyncFailures+1,4);runOnUiThread(this::updateSyncIndicator);}
            finally{foregroundSyncBusy=false;}
        });
    }

    private void showStartupLoading(){
        if(content==null)return;
        startupLoading=true;
        startupCompletionScheduled=false;
        startupStartedAt=System.currentTimeMillis();
        // La progression principale est désormais au centre autour du logo.
        if(syncBanner!=null)syncBanner.setVisibility(View.GONE);
        content.removeAllViews();
        LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);wrap.setGravity(Gravity.CENTER);wrap.setPadding(dp(24),dp(30),dp(24),dp(28));wrap.setMinimumHeight(dp(520));

        FrameLayout logoStage=new FrameLayout(this);
        LinearLayout.LayoutParams stageLp=new LinearLayout.LayoutParams(dp(300),dp(300));stageLp.setMargins(0,0,0,dp(8));
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.unite_paintball_logo);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams logoLp=new FrameLayout.LayoutParams(dp(232),dp(232),Gravity.CENTER);
        logoStage.addView(logo,logoLp);
        // Anneau volontairement ajouté APRES le logo : il passe au-dessus des contours de la photo.
        startupRing=new StartupRingView();
        logoStage.addView(startupRing,new FrameLayout.LayoutParams(-1,-1));
        wrap.addView(logoStage,stageLp);

        startupCenterProgressText=tv("0 %",18,true,LIME);startupCenterProgressText.setGravity(Gravity.CENTER);wrap.addView(startupCenterProgressText);
        TextView h=tv("Unité Paintball",28,true,TEXT);h.setGravity(Gravity.CENTER);h.setPadding(0,dp(10),0,dp(8));wrap.addView(h);
        startupCenterStatus=tv("Chargement…",14,false,MUTED);startupCenterStatus.setGravity(Gravity.CENTER);wrap.addView(startupCenterStatus);
        ProgressBar startupSpinner=new ProgressBar(this);LinearLayout.LayoutParams spinLp=new LinearLayout.LayoutParams(dp(44),dp(44));spinLp.gravity=Gravity.CENTER;spinLp.setMargins(0,dp(16),0,0);wrap.addView(startupSpinner,spinLp);
        TextView music=tv("♪ Musique continue pendant le chargement",12,true,LIME);music.setGravity(Gravity.CENTER);music.setPadding(0,dp(18),0,0);wrap.addView(music);
        content.addView(wrap,new LinearLayout.LayoutParams(-1,-1));
    }

    private void startStartupMusic(){
        startupSoundEnabled=getSharedPreferences("launcher_prefs",MODE_PRIVATE).getBoolean("sound_enabled",true);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        // Reprend le lecteur déjà lancé par LauncherActivity sans repartir du début.
        StartupAudioController.startOrContinue(this,startupSoundEnabled);
    }

    private void stopStartupMusic(){
        StartupAudioController.release();
    }

    private boolean automaticPacksRunning=false;
    private void finishStartupAndRender(){
        Avatar4KPack avatars=new Avatar4KPack(this);Badge4KPack badges=new Badge4KPack(this);
        if(!avatars.installed()||!badges.installed()){
            if(automaticPacksRunning)return;if(!startupLoading)showStartupLoading();automaticPacksRunning=true;
            setStartupProgress(0,"Préparation automatique des textures 4K…");
            io.execute(()->{try{
                PackInstaller.Progress progress=(state,done,total)->runOnUiThread(()->{if(!isFinishing())setStartupProgress((int)(total>0?done*100/total:0),state+" des ressources 4K · "+(done/1048576)+" / "+(total/1048576)+" Mo");});
                if(!avatars.installed())avatars.install(progress);if(!badges.installed())badges.install(progress);
                runOnUiThread(()->{automaticPacksRunning=false;if(!isFinishing())finishStartupAndRender();});
            }catch(Exception e){runOnUiThread(()->{automaticPacksRunning=false;if(!isFinishing())handleStartupFailure("Installation automatique des ressources : "+e.getMessage());});}});return;
        }
        if(startupCompletionScheduled)return;
        startupCompletionScheduled=true;
        setStartupProgress(100,"Prêt");
        long elapsed=Math.max(0L,System.currentTimeMillis()-startupStartedAt);
        long wait=Math.max(0L,MIN_NATIVE_STARTUP_MS-elapsed);
        handler.postDelayed(()->{
            startupLoading=false;
            startupCompletionScheduled=false;
            stopStartupMusic();
            render();
            if(syncBanner!=null)syncBanner.setVisibility(backgroundFullSyncRunning?View.VISIBLE:View.GONE);
        },wait);
    }

    private void setStartupProgress(int value,String message){
        if(!startupLoading)return;
        int safe=Math.max(0,Math.min(100,value));
        if(startupRing!=null)startupRing.setProgressValue(safe);
        if(startupCenterProgressText!=null){
            startupCenterProgressText.setText(safe+" %");
            startupCenterProgressText.setTextColor(safe<76?LIME:ORANGE);
        }
        if(startupCenterStatus!=null&&message!=null)startupCenterStatus.setText(message);
        // Le petit bandeau supérieur reste masqué pendant le lancement : l’anneau central est la référence visuelle.
        if(syncBanner!=null)syncBanner.setVisibility(View.GONE);
    }

    private void mergePlatformSnapshot(JSONObject platform){
        try{
            if(snapshot==null)snapshot=new JSONObject();
            JSONObject user=platform.optJSONObject("user");JSONObject profile=platform.optJSONObject("profile");JSONObject latest=platform.optJSONObject("latestBooking");
            if(user!=null)snapshot.put("user",user);if(profile!=null)snapshot.put("profile",profile);
            snapshot.put("administrator",platform.optBoolean("administrator",snapshot.optBoolean("administrator",false)));
            snapshot.put("next",latest==null?JSONObject.NULL:latest);
            JSONArray upcoming=new JSONArray();if(latest!=null)upcoming.put(latest);snapshot.put("upcoming",upcoming);snapshot.put("ownBooking",latest!=null);
            JSONArray messages=platform.optJSONArray("messages");if(messages!=null)snapshot.put("messageCount",messages.length());
            JSONArray notifications=platform.optJSONArray("notifications");if(notifications!=null)snapshot.put("alerts",notifications);
            JSONArray qrCodes=platform.optJSONArray("qrCodes");if(qrCodes!=null)snapshot.put("qrCodes",qrCodes);
            JSONArray bookingParticipants=platform.optJSONArray("bookingParticipants");if(bookingParticipants!=null)snapshot.put("bookingParticipants",bookingParticipants);
            store.saveCache(snapshot.toString());
        }catch(Exception ignored){}
    }

    private void handleStartupFailure(String message){
        fullSyncIncomplete=true;
        if(syncStatus!=null)syncStatus.setText("Synchronisation incomplète");
        updateSyncIndicator();
        startupLoading=false;stopStartupMusic();content.removeAllViews();
        LinearLayout c=card();
        c.addView(tv("Synchronisation incomplète",22,true,TEXT));
        c.addView(tv((message==null||message.isEmpty()?"Certaines données n’ont pas pu être synchronisées.":message)+" L’application reste sur cet écran afin de ne pas afficher une version partiellement synchronisée.",14,false,MUTED));
        Button retry=button("Réessayer la synchronisation");
        retry.setOnClickListener(v->{showStartupLoading();startStartupMusic();startCriticalStartupSync();});
        c.addView(retry);
        if(snapshot!=null){
            Button offline=secondaryButton("Continuer avec la copie hors ligne");
            offline.setOnClickListener(v->{startupLoading=false;render();});
            c.addView(offline);
            c.addView(tv("Mode hors ligne : les informations peuvent ne pas être à jour.",12,false,MUTED));
        }
        content.addView(c);
    }

    private boolean hasInternetConnection(){
        try{
            ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            if(cm==null)return false;
            Network network=cm.getActiveNetwork();
            if(network==null)return false;
            NetworkCapabilities caps=cm.getNetworkCapabilities(network);
            return caps!=null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }catch(Exception e){return false;}
    }

    private void updateSyncIndicator(){
        if(syncIndicator==null||store==null)return;
        int pending=SyncApi.pendingCount(this)+MediaSyncQueue.pendingCount(this);
        int blocked=SyncApi.blockedCount(this)+MediaSyncQueue.blockedCount(this);
        long last=store.lastSuccessSync();
        String when=last>0?new SimpleDateFormat("HH:mm",Locale.FRANCE).format(new Date(last)):"jamais";
        boolean online=hasInternetConnection();
        String text; int color;
        if(!online || foregroundSyncFailures>0 || fullSyncIncomplete){
            text=(!online?"● Hors ligne · dernière synchro ":"● Synchronisation incomplète · dernière synchro ")+when+(pending>0?" · "+pending+" en attente":"");
            color=ORANGE;
        }else if(blocked>0){
            text="● Vérification requise · "+blocked+" refusé(s) · "+pending+" en attente";
            color=ORANGE;
        }else if(pending>0){
            text="● "+pending+" en attente d’envoi · dernière synchro "+when;
            color=ORANGE;
        }else{
            text=StartupSyncPolicy.hasCompletedSync(last)?"● Synchronisé · "+when:"● En attente de synchronisation";
            color=LIME;
        }
        syncIndicator.setText(text);
        syncIndicator.setTextColor(color);
    }

    private void render() {
        if(content==null)return;
        adminSubpage=false;
        if(!section.equals(lastRenderedSection)){
            if(!restoringHistory && lastRenderedSection!=null && !lastRenderedSection.isEmpty()) sectionHistory.push(lastRenderedSection);
            lastRenderedSection=section;
            restoringHistory=false;
        }
        if(!"Messages".equals(section)){ activeChatChannel=""; activeChatLabel=""; activeChatSignature=""; handler.removeCallbacks(chatSync); }
        content.removeAllViews(); title.setText(section); updateModeSwitch(); updateNavSelection(); updateSyncIndicator();
        if(snapshot!=null){Button complete=secondaryButton("Ouvrir les fonctions complètes");complete.setOnClickListener(v->openSharedFeature(SharedFeaturePolicy.pageForSection(section)));content.addView(complete);}
        if(snapshot==null){ showStartupLoading(); return; }
        switch(section){
            case "Jouer": renderPlayHub(); break;
            case "Validations admin": renderAdminValidationsHub(); break;
            case "Terrain admin": renderAdminTerrainHub(); break;
            case "Joueurs admin": loadAdminPlayers(); break;
            case "Gestion admin": renderAdminManagementHub(); break;
            case "Réservations": renderBookings(); break;
            case "Nouvelle réservation": renderNewBooking(); break;
            case "Événements": renderEvents(); break;
            case "Actualités": renderNewsFeed(); break;
            case "Parties ouvertes": renderOpenGames(); break;
            case "Adhésions": renderMemberships(); break;
            case "Messages": renderMessages(); break;
            case "Notifications": renderNotifications(); break;
            case "Galerie": renderGallery(); break;
            case "Joueurs": renderPlayers(); break;
            case "Points": renderRewards(); break;
            case "Arena": renderArena(); break;
            case "Live": renderLive(); break;
            case "Tournois": renderTournaments(); break;
            case "QR": renderQr(); break;
            case "Profil": renderProfile(); break;
            case "Espace joueur": renderPlayerHub(); break;
            case "Paiements": renderPayments(); break;
            case "Documents": renderDocuments(); break;
            case "Avant de venir": renderBeforeVisit(); break;
            case "Amis": renderFriends(); break;
            case "Groupes": renderGroups(); break;
            case "Attente": renderWaitingList(); break;
            case "Paramètres": renderSettings(); break;
            case "Sécurité": renderSafety(); break;
            case "Finance admin": renderAdminFinance(); break;
            case "Stocks": renderStocksAndEquipment(); break;
            case "Incidents": renderIncidents(); break;
            case "Scénarios": renderScenarios(); break;
            case "Assistant IA": renderAiAssistant(); break;
            case "Aide": renderHelp(); break;
            case "Admin": renderAdmin(); break;
            default: renderHome();
        }
    }

    private void renderPlayerHub(){
        JSONObject p=snapshot.optJSONObject("profile");
        JSONObject n=snapshot.optJSONObject("next");
        String pseudo=p==null?"Joueur":p.optString("nickname",p.optString("pseudo",join(p.optString("firstName"),p.optString("lastName"))));
        if(pseudo==null||pseudo.trim().isEmpty()) pseudo="Joueur";
        content.addView(sectionIntro("ESPACE JOUEUR","Bonjour "+pseudo,"Ta prochaine partie et tes informations essentielles, au même endroit."));

        LinearLayout shortcuts=row();
        LinearLayout a=siteQuickCard("▣","Ma partie",n==null?"Aucune réservation":"Voir ma prochaine réservation",true);a.setOnClickListener(v->{section="Réservations";render();});
        LinearLayout b=siteQuickCard("€","Paiements","Montants et reste à payer",false);b.setOnClickListener(v->{section="Paiements";render();});
        LinearLayout.LayoutParams lp1=new LinearLayout.LayoutParams(0,dp(155),1);lp1.setMargins(0,dp(8),dp(5),dp(10));
        LinearLayout.LayoutParams lp2=new LinearLayout.LayoutParams(0,dp(155),1);lp2.setMargins(dp(5),dp(8),0,dp(10));
        shortcuts.addView(a,lp1);shortcuts.addView(b,lp2);content.addView(shortcuts);

        LinearLayout next=card();next.setBackground(makeBg(CARD_2,18,ORANGE));next.addView(tv("PROCHAINE PARTIE",12,true,ORANGE));
        if(n==null){next.addView(tv("Aucune partie prévue",24,true,TEXT));next.addView(tv("Réserve une session pour afficher ici ton accès rapide, ton QR et la confirmation de présence.",13,false,MUTED));}
        else{
            next.addView(tv(n.optString("title","Paintball Unité"),25,true,TEXT));
            next.addView(tv(n.optString("date","")+" · "+n.optString("time","")+" · Pont-du-Navoy",16,false,TEXT));
            next.addView(infoRow("Référence",n.optString("reference","—")));
            next.addView(infoRow("Participants",String.valueOf(n.optInt("players",1))));
            next.addView(infoRow("Statut",n.optString("status","pending")));
            LinearLayout r=row();Button qr=button("Afficher mon QR");qr.setOnClickListener(v->{section="QR";render();});Button booking=secondaryButton("Voir ma réservation");booking.setOnClickListener(v->{section="Réservations";render();});r.addView(qr,new LinearLayout.LayoutParams(0,dp(52),1));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(52),1);bp.setMargins(dp(10),0,0,0);r.addView(booking,bp);next.addView(r);
        }
        content.addView(next);

        LinearLayout stats=row();
        stats.addView(stat("Messages",String.valueOf(snapshot.optInt("messageCount",0))),weight());
        JSONArray al=snapshot.optJSONArray("alerts");stats.addView(stat("Notifications",String.valueOf(al==null?0:al.length())),weight());
        stats.addView(stat("Points",String.valueOf(snapshot.optInt("points",0))),weight());
        content.addView(stats);

        addHubButton("Checklist avant de venir","Avant de venir");
        addHubButton("Mes documents et autorisations","Documents");
        addHubButton("Liste d’attente","Attente");
        addHubButton("Historique, points et badges","Points");
        addHubButton("Mes amis","Amis");
        addHubButton("Groupes et invitations","Groupes");
        addHubButton("Galerie","Galerie");
        addHubButton("Mes tournois","Tournois");
    }

    private void addHubButton(String label,String target){Button b=secondaryButton(label);b.setOnClickListener(v->{section=target;render();});content.addView(b);}

    private void renderPayments(){
        content.addView(sectionIntro("ESPACE JOUEUR","Mes paiements","Les montants proviennent de tes réservations. Le règlement reste effectué sur place."));
        JSONArray arr=snapshot.optJSONArray("upcoming");
        if(arr==null||arr.length()==0){content.addView(cardText("Aucun paiement à afficher","Tes réservations apparaîtront ici avec le total, le reçu et le reste à payer."));return;}
        for(int i=0;i<arr.length();i++){JSONObject b=arr.optJSONObject(i);if(b==null)continue;double total=b.optDouble("total",b.optDouble("amount",0));double paid=b.optDouble("paid",0);LinearLayout c=card();c.addView(tv(b.optString("reference","Réservation"),22,true,TEXT));c.addView(tv(b.optString("date","")+" · "+b.optString("time",""),13,false,MUTED));c.addView(statusPill(paid>=total&&total>0?"Payé":"À payer"));LinearLayout r=row();r.addView(stat("Total",String.format(Locale.FRANCE,"%.2f €",total)),weight());r.addView(stat("Reçu",String.format(Locale.FRANCE,"%.2f €",paid)),weight());r.addView(stat("Reste",String.format(Locale.FRANCE,"%.2f €",Math.max(0,total-paid))),weight());c.addView(r);content.addView(c);}
    }


    private void renderDocuments(){
        content.addView(sectionIntro("DOCUMENTS","Mes documents","Retrouve les chartes, autorisations, consentements et justificatifs liés à ton compte."));
        addDocumentCard("Charte de participation et de sécurité","Règles de jeu, masque, lanceur, comportement et risques.",()->showDocumentText("Charte de participation et de sécurité",charterDocumentText()));
        addDocumentCard("Règlement intérieur","Accès au terrain, horaires, sécurité, réservations et comportement.",()->showDocumentText("Règlement intérieur",rulesDocumentText()));
        addDocumentCard("Conditions de réservation","Prix, modifications, annulation, retards, absences et paiement sur place.",()->showDocumentText("Conditions de réservation",bookingConditionsText()));
        addDocumentCard("Politique de confidentialité","Données collectées, finalités, droits et durées de conservation.",()->showDocumentText("Politique de confidentialité",privacyDocumentText()));
        addDocumentCard("Autorisation parentale","Pour chaque joueur mineur : identité du responsable légal, consentements et signature.",this::openParentalAuthorizations);
        addDocumentCard("Consentement photo / vidéo","Autorisation distincte, facultative et révocable pour l’avenir.",()->showDocumentText("Consentement photo / vidéo",imageConsentText()));
        addDocumentCard("Consignes de sécurité","Masque, zones neutres, lanceur, arbitre et conduite à tenir en cas de problème.",()->showDocumentText("Consignes de sécurité",safetyDocumentText()));

        LinearLayout state=card();state.addView(tv("État de mon dossier",20,true,TEXT));state.addView(infoRow("Identité","À vérifier sur place si nécessaire"));state.addView(infoRow("Charte","À signer selon la réservation"));state.addView(infoRow("Briefing sécurité","Enregistré le jour de la partie"));
        JSONArray participants=snapshot==null?null:snapshot.optJSONArray("bookingParticipants");int minors=0;int ready=0;if(participants!=null)for(int i=0;i<participants.length();i++){JSONObject x=participants.optJSONObject(i);if(x!=null&&x.optBoolean("minor",false)){minors++;String st=x.optString("parentalStatus","manquante");if("validated".equalsIgnoreCase(st)||"validée".equalsIgnoreCase(st))ready++;}}
        state.addView(infoRow("Mineurs",minors==0?"Aucun mineur détecté":ready+" / "+minors+" autorisation(s) validée(s)"));content.addView(state);
    }

    private void addDocumentCard(String name,String description,Runnable action){
        LinearLayout c=card();c.addView(tv(name,18,true,TEXT));TextView x=tv(description,13,false,MUTED);x.setPadding(0,dp(6),0,dp(8));c.addView(x);Button b=secondaryButton("Consulter");b.setOnClickListener(v->action.run());c.addView(b);content.addView(c);
    }

    private void showDocumentText(String name,String body){
        ScrollView sv=new ScrollView(this);TextView text=tv(body,14,false,TEXT);text.setLineSpacing(0,1.25f);text.setPadding(dp(18),dp(12),dp(18),dp(18));sv.addView(text);new AlertDialog.Builder(this).setTitle(name).setView(sv).setPositiveButton("Fermer",null).show();
    }

    private String charterDocumentText(){return "1. Le masque homologué reste porté dans toutes les zones de jeu.\n\n2. Le lanceur reste sécurisé hors zone de jeu et ne doit jamais être dirigé vers une personne sans masque.\n\n3. Les instructions de l'arbitre et les limites du terrain doivent être respectées.\n\n4. Tout incident, douleur, malaise ou défaut de matériel doit être signalé immédiatement.\n\n5. Le participant s'engage à adopter un comportement respectueux et à ne pas jouer sous l'emprise d'alcool ou de substances altérant la vigilance.\n\n6. Les risques inhérents au paintball (impacts, chutes, collisions, fatigue) sont expliqués sans exclure les obligations légales de sécurité de l'organisateur.";}
    private String rulesDocumentText(){return "Accès au terrain uniquement aux horaires autorisés et après validation de l'organisation. Le matériel prêté doit être utilisé conformément aux consignes et rendu complet. Les zones neutres, zones interdites et consignes de l'arbitre sont obligatoires. Une personne qui met en danger les autres peut être exclue de la partie. Les absences, retards et comportements répétés peuvent nécessiter une validation manuelle des réservations suivantes.";}
    private String bookingConditionsText(){return "La réservation indique une date, un créneau, un nombre de joueurs et des options. Le terrain accueille au maximum 20 joueurs simultanément ; les groupes de 21 à 40 participants sont organisés en rotations Groupe A / Groupe B entre les manches. Le montant affiché reste soumis à validation lorsque le site le précise. Le paiement est réalisé sur place ; aucune donnée bancaire n'est demandée par l'application. Toute modification ou annulation doit être transmise dès que possible. Une confirmation de présence peut être demandée avant la séance. En cas d'absence sans réponse, le créneau peut être libéré et l'absence enregistrée.";}
    private String privacyDocumentText(){return "Les données sont utilisées pour gérer le compte, les réservations, la sécurité, les contacts d'urgence, les consentements et les communications nécessaires. Une pièce d'identité peut être vérifiée visuellement sur place mais sa photographie n'est pas enregistrée par l'application. Les données locales nécessaires au fonctionnement hors connexion sont conservées sur le téléphone et synchronisées avec le site dès que le réseau revient. Les demandes d'accès, rectification ou suppression doivent être adressées à Unité Paintball.";}
    private String imageConsentText(){return "L'autorisation photo / vidéo est facultative et distincte de la participation. Refuser n'empêche pas de jouer. Lorsqu'elle est accordée, elle peut préciser les supports autorisés (site, réseaux sociaux, affiches, galerie). Elle peut être retirée pour l'avenir ; ce retrait ne remet pas en cause les utilisations déjà réalisées licitement avant la demande.";}
    private String safetyDocumentText(){return "Avant chaque partie : vérifier le masque, le lanceur et les équipements. Pendant le jeu : garder le masque, respecter les distances et les limites du terrain, ne pas tirer en zone neutre et suivre l'arbitre. En cas de problème : arrêter de jouer, sécuriser le lanceur, prévenir immédiatement l'arbitre et rejoindre la zone indiquée. En cas de blessure ou malaise, l'organisation peut interrompre l'activité et contacter la personne d'urgence ou les secours.";}

    private void openParentalAuthorizations(){
        JSONArray participants=snapshot==null?null:snapshot.optJSONArray("bookingParticipants");ArrayList<JSONObject> minors=new ArrayList<>();if(participants!=null)for(int i=0;i<participants.length();i++){JSONObject p=participants.optJSONObject(i);if(p!=null&&p.optBoolean("minor",false))minors.add(p);}
        if(minors.isEmpty()){showDocumentText("Autorisation parentale","Aucun joueur mineur n'est actuellement détecté dans ta dernière réservation. Lorsqu'un participant de moins de 18 ans est ajouté, un dossier distinct est créé pour lui et le responsable légal doit le signer avant la partie.");return;}
        String[] labels=new String[minors.size()];for(int i=0;i<minors.size();i++){JSONObject p=minors.get(i);String st=p.optString("parentalStatus","manquante");labels[i]=p.optString("participantName","Joueur mineur")+" · "+prettyKey(st);}
        new AlertDialog.Builder(this).setTitle("Autorisations parentales").setItems(labels,(d,which)->{JSONObject p=minors.get(which);String token=p.optString("qrToken","");if(token.isEmpty()){Toast.makeText(this,"Lien d'autorisation indisponible. Actualise l'application.",Toast.LENGTH_LONG).show();return;}Intent in=new Intent(Intent.ACTION_VIEW,Uri.parse(AppConfig.BASE_URL+"/dossier-mineur/"+Uri.encode(token)));startActivity(in);}).setNegativeButton("Fermer",null).show();
    }

    private void promptMinorDossiers(JSONObject result){
        JSONObject booking=result==null?null:result.optJSONObject("booking");JSONArray arr=booking==null?null:booking.optJSONArray("minorDossiers");if(arr==null||arr.length()==0){section="Réservations";refresh(true);return;}
        ArrayList<JSONObject> rows=new ArrayList<>();for(int i=0;i<arr.length();i++){JSONObject x=arr.optJSONObject(i);if(x!=null)rows.add(x);}
        String[] labels=new String[rows.size()];for(int i=0;i<rows.size();i++)labels[i]="Signer pour "+rows.get(i).optString("participantName","joueur mineur");
        new AlertDialog.Builder(this).setTitle("Autorisation parentale requise").setMessage("La réservation est enregistrée. Chaque joueur mineur doit avoir une autorisation parentale signée avant de jouer.").setItems(labels,(d,which)->{String token=rows.get(which).optString("qrToken","");if(!token.isEmpty())startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(AppConfig.BASE_URL+"/dossier-mineur/"+Uri.encode(token))));}).setPositiveButton("Plus tard",(d,w)->{section="Documents";refresh(true);}).setOnDismissListener(d->{if(!"Documents".equals(section)){section="Réservations";refresh(true);}}).show();
    }

    private void renderWaitingList(){
        content.addView(sectionIntro("RÉSERVATIONS","Liste d’attente","Quand une session est complète, inscris-toi sur la vraie liste d’attente du site et suis ta position."));
        LinearLayout join=card();join.addView(tv("Rejoindre une liste d’attente",20,true,TEXT));
        EditText date=editField("Date (AAAA-MM-JJ)",new SimpleDateFormat("yyyy-MM-dd",Locale.FRANCE).format(new Date()));
        Spinner time=spinner(new String[]{"09:00","10:00","11:00","12:00","13:00","14:00","15:00","16:00","17:00","18:00"});time.setSelection(5);
        EditText players=editField("Nombre de joueurs","1");players.setInputType(InputType.TYPE_CLASS_NUMBER);
        join.addView(date);join.addView(fieldWrap("Heure",time));join.addView(players);
        Button add=button("Me mettre sur liste d’attente");join.addView(add);content.addView(join);
        add.setOnClickListener(v->{int count;try{count=Math.max(1,Math.min(16,Integer.parseInt(players.getText().toString().trim())));}catch(Exception e){Toast.makeText(this,"Nombre de joueurs invalide",Toast.LENGTH_LONG).show();return;}JSONObject body=new JSONObject();try{body.put("action","join_waitlist");body.put("gameDate",date.getText().toString().trim());body.put("startTime",String.valueOf(time.getSelectedItem()));body.put("players",count);}catch(Exception ignored){}add.setEnabled(false);io.execute(()->{try{SyncApi.post(this,"/api/operations",store.token(),body);runOnUiThread(()->{Toast.makeText(this,"Ajouté à la liste d’attente",Toast.LENGTH_SHORT).show();content.removeAllViews();renderWaitingList();});}catch(Exception e){runOnUiThread(()->{add.setEnabled(true);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});});
        LinearLayout current=card();current.addView(tv("Mes demandes",20,true,TEXT));content.addView(current);
        loadJson("/api/operations",data->{JSONArray waits=data.optJSONArray("waitlist");current.removeViews(1,Math.max(0,current.getChildCount()-1));if(waits==null||waits.length()==0){current.addView(tv("Aucune inscription en attente.",13,false,MUTED));return;}for(int i=0;i<waits.length();i++){JSONObject w=waits.optJSONObject(i);if(w==null)continue;String when=w.optString("gameDate","—")+" · "+w.optString("startTime","—");current.addView(infoRow(when,"Position "+w.optInt("position",0)));current.addView(tv(w.optInt("players",1)+" joueur(s) · "+prettyKey(w.optString("status","waiting")),12,false,MUTED));}});
    }

    private void renderAdminFinance(){
        content.addView(sectionIntro("ADMINISTRATION","Centre comptable","Tableau de bord connecté au site : encaissements, dépenses, acomptes, remboursements, avoirs et fonds personnels."));

        int pending=SyncApi.pendingCount(this)+MediaSyncQueue.pendingCount(this), blocked=SyncApi.blockedCount(this)+MediaSyncQueue.blockedCount(this);
        LinearLayout sync=card();
        sync.setBackground(makeBg(pending>0?(AppUiPrefs.isLight(this)?Color.rgb(255,246,226):Color.rgb(47,39,17)):CARD_2,16,pending>0?ORANGE:LIME));
        sync.addView(tv(pending>0?"Synchronisation à terminer":"Comptabilité synchronisée",18,true,pending>0?ORANGE:LIME));
        sync.addView(tv(pending>0?(pending+" modification(s) sont conservée(s) sur ce téléphone. "+(blocked>0?blocked+" nécessitent une vérification car le site les a refusées.":"Elles seront renvoyées automatiquement au site.")):"Les écritures réalisées dans l’application sont envoyées au site et les données du site sont relues automatiquement.",13,false,MUTED));
        if(pending>0){Button push=button("Envoyer maintenant");push.setOnClickListener(v->io.execute(()->{int sent=SyncApi.flush(this,store.token(),50)+MediaSyncQueue.flush(this,store.token(),10);runOnUiThread(()->{Toast.makeText(this,sent>0?sent+" élément(s) envoyé(s)":"Aucun envoi effectué",Toast.LENGTH_SHORT).show();render();});}));sync.addView(push);}content.addView(sync);

        LinearLayout actions=row();
        Button income=button("+ Encaissement");income.setOnClickListener(v->showAccountingEntryForm("payment"));
        Button expense=secondaryButton("+ Dépense");expense.setOnClickListener(v->showAccountingEntryForm("expense"));
        LinearLayout.LayoutParams a1=new LinearLayout.LayoutParams(0,dp(52),1);a1.setMargins(0,0,dp(7),dp(10));
        LinearLayout.LayoutParams a2=new LinearLayout.LayoutParams(0,dp(52),1);a2.setMargins(dp(7),0,0,dp(10));
        actions.addView(income,a1);actions.addView(expense,a2);content.addView(actions);

        LinearLayout financeNav=card();financeNav.addView(tv("Gestion",18,true,TEXT));
        String[][] financeItems={{"Encaissements","Paiements, ventes et règlements","payment"},{"Acomptes","Sommes versées avant la partie","deposit"},{"Dépenses / achats","Fournisseurs, entretien et charges","expense"},{"Avances personnelles","Sommes encaissées puis à reverser","personal"},{"Reçus / factures / avoirs","Documents comptables disponibles","documents"},{"Tous les mouvements","Recherche et contrôle chronologique","all"}};
        for(String[] fi:financeItems){LinearLayout line=infoRow(fi[0],fi[1]+"  ›");final String fk=fi[2],ft=fi[0];line.setPadding(0,dp(9),0,dp(9));line.setOnClickListener(v->{if("personal".equals(fk))loadPersonalFunds();else showFinanceCategory(ft,fk);});financeNav.addView(line);}content.addView(financeNav);

        loadJson("/api/management?admin=1", data -> {
            JSONArray entries=extractArray(data,"accounting","entries","movements","transactions");
            double incomeTotal=0, expenseTotal=0, deposits=0, refunds=0, todayNet=0, monthNet=0;
            double cash=0,card=0,transfer=0,check=0,personal=0,other=0;int movements=0;
            String today=new SimpleDateFormat("yyyy-MM-dd",Locale.FRANCE).format(new Date());String month=today.substring(0,7);
            if(entries!=null)for(int i=0;i<entries.length();i++){
                JSONObject e=entries.optJSONObject(i);if(e==null)continue;movements++;
                double signed=signedAccountingAmount(e), abs=Math.abs(signed);String kind=e.optString("kind",e.optString("type",""));String d=e.optString("operationDate",e.optString("date",""));
                if(signed>=0)incomeTotal+=signed;else expenseTotal+=abs;
                if("deposit".equals(kind)||"acompte".equalsIgnoreCase(kind))deposits+=abs;
                if("refund".equals(kind)||"credit".equals(kind))refunds+=abs;
                if(today.equals(d))todayNet+=signed;if(d.startsWith(month))monthNet+=signed;
                String pm=e.optString("paymentMethod","");if("cash".equals(pm))cash+=abs;else if("card".equals(pm))card+=abs;else if("transfer".equals(pm)||"association_transfer".equals(pm))transfer+=abs;else if("check".equals(pm))check+=abs;else if("personal_pending".equals(pm))personal+=abs;else other+=abs;
            }
            double balance=incomeTotal-expenseTotal;
            LinearLayout m1=row();m1.addView(stat("Solde",moneySigned(balance)),weight());m1.addView(stat("Recettes",money(incomeTotal)),weight());content.addView(m1);
            LinearLayout m2=row();m2.addView(stat("Dépenses",money(expenseTotal)),weight());m2.addView(stat("Ce mois",moneySigned(monthNet)),weight());content.addView(m2);
            LinearLayout m3=row();m3.addView(stat("Aujourd’hui",moneySigned(todayNet)),weight());m3.addView(stat("Acomptes",money(deposits)),weight());content.addView(m3);
            LinearLayout m4=row();m4.addView(stat("Remb./avoirs",money(refunds)),weight());m4.addView(stat("Mouvements",String.valueOf(movements)),weight());content.addView(m4);

            LinearLayout methods=card();methods.addView(tv("Répartition des règlements",19,true,TEXT));
            methods.addView(infoRow("Espèces",money(cash)));methods.addView(infoRow("Carte",money(card)));methods.addView(infoRow("Virement",money(transfer)));methods.addView(infoRow("Chèque",money(check)));methods.addView(infoRow("À reverser",money(personal)));if(other>0)methods.addView(infoRow("Autres",money(other)));content.addView(methods);

            LinearLayout recent=card();recent.addView(tv("Derniers mouvements",20,true,TEXT));
            if(entries==null||entries.length()==0)recent.addView(tv("Aucun mouvement comptable enregistré.",13,false,MUTED));
            else for(int i=0;i<Math.min(entries.length(),15);i++){JSONObject e=entries.optJSONObject(i);if(e==null)continue;double signed=signedAccountingAmount(e);LinearLayout line=infoRow(e.optString("operationDate",e.optString("date","—")),moneySigned(signed));recent.addView(line);String kind=e.optString("kind",e.optString("type","Mouvement"));String description=e.optString("description",prettyKey(kind));String pm=e.optString("paymentMethod","");recent.addView(tv(description+(pm.isEmpty()?"":" · "+prettyKey(pm)),12,false,MUTED));}
            content.addView(recent);

            Button export=secondaryButton("Exporter les mouvements en CSV");final JSONArray exportEntries=entries==null?new JSONArray():entries;export.setOnClickListener(v->launchAccountingCsvExport(exportEntries));content.addView(export);
            Button refresh=button("Synchroniser maintenant");refresh.setOnClickListener(v->{io.execute(()->{SyncApi.flush(this,store.token(),50);MediaSyncQueue.flush(this,store.token(),10);});section="Finance admin";render();});content.addView(refresh);
        });
    }

    private void showFinanceCategory(String titleText,String filterKind){
        content.removeAllViews();adminSubpage=true;content.addView(sectionIntro("COMPTABILITÉ",titleText,"Lecture directe du site avec recherche locale instantanée."));
        if("documents".equals(filterKind)){
            loadJson("/api/management?admin=1",data->{
                JSONArray docs=data.optJSONArray("accountingDocuments");
                if(docs==null||docs.length()==0)content.addView(cardText("Aucun document","Aucun reçu, facture ou avoir n’est actuellement enregistré dans la comptabilité du site."));
                else for(int i=0;i<Math.min(docs.length(),80);i++){
                    JSONObject d=docs.optJSONObject(i);if(d==null)continue;
                    LinearLayout c=card();
                    String fileName=d.optString("fileName","Document comptable");
                    c.addView(tv(fileName,17,true,TEXT));
                    c.addView(infoRow("Type",d.optString("mimeType","fichier")));
                    c.addView(infoRow("Écriture",d.optString("accountingEntryId","—")));
                    String key=d.optString("objectKey","");
                    if(!key.isEmpty()){Button dl=button("Télécharger le justificatif");dl.setOnClickListener(v->downloadAccountingDocument(d));c.addView(dl);}
                    content.addView(c);
                }
                Button back=secondaryButton("← Retour à la comptabilité");back.setOnClickListener(v->{adminSubpage=false;section="Finance admin";render();});content.addView(back);
            });return;
        }
        LinearLayout filters=card();filters.addView(tv("Rechercher / filtrer",18,true,TEXT));EditText search=editField("Nom, motif, réservation, moyen de paiement…","");Spinner period=spinner(new String[]{"Toutes les périodes","Aujourd’hui","Ce mois","30 derniers jours","Mois précédent","Cette année","Année précédente"});filters.addView(search);filters.addView(fieldWrap("Période",period));content.addView(filters);
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);content.addView(list);
        loadJson("/api/management?admin=1",data->{JSONArray entries=extractArray(data,"accounting","entries","movements","transactions");final JSONArray source=entries==null?new JSONArray():entries;Runnable apply=()->renderFinanceList(list,source,filterKind,search.getText().toString(),period.getSelectedItemPosition());apply.run();search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){apply.run();}public void afterTextChanged(Editable e){}});period.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){apply.run();}public void onNothingSelected(android.widget.AdapterView<?> p){}});
            Button add="expense".equals(filterKind)?button("+ Ajouter une dépense"):button("+ Enregistrer un encaissement");add.setOnClickListener(v->showAccountingEntryForm("expense".equals(filterKind)?"expense":"payment"));content.addView(add);Button export=secondaryButton("Exporter cette vue en CSV");export.setOnClickListener(v->launchAccountingCsvExport(filteredAccounting(source,filterKind,search.getText().toString(),period.getSelectedItemPosition())));content.addView(export);Button back=secondaryButton("← Retour à la comptabilité");back.setOnClickListener(v->{adminSubpage=false;section="Finance admin";render();});content.addView(back);
        });
    }

    private void downloadAccountingDocument(JSONObject document){
        String key=document==null?"":document.optString("objectKey","");
        if(key.isEmpty()){Toast.makeText(this,"Clé du justificatif indisponible",Toast.LENGTH_LONG).show();return;}
        String fileName=document.optString("fileName","justificatif-comptable");
        String mime=document.optString("mimeType","application/octet-stream");
        Toast.makeText(this,"Téléchargement du justificatif…",Toast.LENGTH_SHORT).show();
        io.execute(()->{java.net.HttpURLConnection connection=null;try{
            java.net.URL url=new java.net.URL(AppConfig.BASE_URL+"/api/accounting-document?key="+Uri.encode(key));
            connection=(java.net.HttpURLConnection)url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);connection.setReadTimeout(30000);
            connection.setRequestProperty("Cookie",store.token());
            connection.setRequestProperty("User-Agent","UnitePaintball-Android/"+AppConfig.APP_VERSION);
            int status=connection.getResponseCode();
            if(status<200||status>=300)throw new java.io.IOException("HTTP "+status);
            String serverType=connection.getContentType();if(serverType!=null&&!serverType.trim().isEmpty())pendingAccountingDocumentMime=serverType.split(";")[0].trim();else pendingAccountingDocumentMime=mime;
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
            try(java.io.InputStream in=connection.getInputStream()){byte[] buffer=new byte[8192];int read,total=0;while((read=in.read(buffer))!=-1){total+=read;if(total>12*1024*1024)throw new java.io.IOException("Fichier trop volumineux");out.write(buffer,0,read);}}
            pendingAccountingDocumentBytes=out.toByteArray();pendingAccountingDocumentName=fileName;
            runOnUiThread(()->{Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(pendingAccountingDocumentMime);intent.putExtra(Intent.EXTRA_TITLE,pendingAccountingDocumentName);startActivityForResult(intent,REQ_ACCOUNTING_DOCUMENT_SAVE);});
        }catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Téléchargement impossible : "+e.getMessage(),Toast.LENGTH_LONG).show());}finally{if(connection!=null)connection.disconnect();}});
    }

    private void showAccountingEntryForm(String initialKind){
        content.removeAllViews();adminSubpage=true;
        content.addView(sectionIntro("COMPTABILITÉ",initialKind.equals("expense")?"Nouvelle dépense":"Nouvel encaissement","L’écriture est envoyée au site immédiatement ou mise en attente sécurisée si le réseau est indisponible."));
        Spinner kind=new Spinner(this);String[] kinds={"Paiement","Acompte","Vente","Dépense","Achat","Remboursement","Avoir"};String[] values={"payment","deposit","sale","expense","purchase","refund","credit"};kind.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,kinds));kind.setSelection(initialKind.equals("expense")?3:0);content.addView(fieldWrap("Nature de l’écriture",kind));
        EditText amount=editField("Montant en euros (ex. 25,50)","");amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);content.addView(amount);
        EditText date=editField("Date (AAAA-MM-JJ)",new SimpleDateFormat("yyyy-MM-dd",Locale.FRANCE).format(new Date()));content.addView(date);
        Spinner method=new Spinner(this);String[] methods={"Espèces","Carte","Chèque","Virement","Association","Encaissement personnel à reverser"};String[] methodValues={"cash","card","check","transfer","association","personal_pending"};method.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,methods));content.addView(fieldWrap("Mode de paiement",method));
        EditText booking=editField("Réservation liée (si applicable)","");content.addView(booking);
        EditText payer=editField("Payeur / fournisseur","");content.addView(payer);
        EditText desc=editField("Description / motif","");desc.setMinLines(2);content.addView(desc);
        LinearLayout warning=card();warning.setBackground(makeBg(AppUiPrefs.isLight(this)?Color.rgb(255,246,226):Color.rgb(40,35,18),14,ORANGE));warning.addView(tv("Traçabilité",16,true,ORANGE));warning.addView(tv("Une écriture comptable n’est pas supprimée silencieusement depuis l’application. Les corrections doivent rester traçables par remboursement, avoir ou nouvelle écriture selon le fonctionnement du site.",12,false,MUTED));content.addView(warning);
        Button save=button("Enregistrer et synchroniser");content.addView(save);
        save.setOnClickListener(v->{String a=amount.getText().toString().trim().replace(',','.');double val;try{val=Double.parseDouble(a);if(val<=0)throw new Exception();}catch(Exception e){Toast.makeText(this,"Montant invalide",Toast.LENGTH_LONG).show();return;}if(date.getText().toString().trim().length()!=10){Toast.makeText(this,"Date invalide",Toast.LENGTH_LONG).show();return;}String kindValue=values[kind.getSelectedItemPosition()];JSONObject body=new JSONObject();try{body.put("action","accounting");body.put("kind",kindValue);body.put("amount",val);body.put("operationDate",date.getText().toString().trim());body.put("paymentMethod",methodValues[method.getSelectedItemPosition()]);body.put("bookingId",booking.getText().toString().trim());body.put("payerName",payer.getText().toString().trim());body.put("description",desc.getText().toString().trim());body.put("collectedBy","Application Android");}catch(Exception ignored){}
            save.setEnabled(false);io.execute(()->{try{JSONObject r=SyncApi.post(this,"/api/management",store.token(),body);boolean queued=r.optBoolean("_queued",false);runOnUiThread(()->{Toast.makeText(this,queued?"Enregistré sur le téléphone · envoi au site en attente":"Écriture enregistrée sur le site",Toast.LENGTH_LONG).show();section="Finance admin";render();});}catch(Exception e){runOnUiThread(()->{save.setEnabled(true);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});
        });
        Button back=secondaryButton("← Retour à la comptabilité");back.setOnClickListener(v->{section="Finance admin";render();});content.addView(back);
    }

    private double signedAccountingAmount(JSONObject e){double a=e.optDouble("amount",0);String k=e.optString("kind",e.optString("type","")).toLowerCase(Locale.ROOT);if(a<0)return a;if("expense".equals(k)||"purchase".equals(k)||"refund".equals(k)||"credit".equals(k))return -a;return a;}
    private String money(double v){return String.format(Locale.FRANCE,"%.2f €",Math.abs(v));}
    private String moneySigned(double v){return String.format(Locale.FRANCE,"%+.2f €",v);}

    private JSONArray filteredAccounting(JSONArray source,String filterKind,String query,int period){JSONArray out=new JSONArray();String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);String today=new SimpleDateFormat("yyyy-MM-dd",Locale.FRANCE).format(new Date());String month=today.substring(0,7);long now=System.currentTimeMillis();for(int i=0;i<source.length();i++){JSONObject e=source.optJSONObject(i);if(e==null)continue;String kind=e.optString("kind",e.optString("type","")).toLowerCase(Locale.ROOT);double signed=signedAccountingAmount(e);boolean match="all".equals(filterKind)||("expense".equals(filterKind)&&(signed<0&&!("refund".equals(kind)||"credit".equals(kind))))||("deposit".equals(filterKind)&&("deposit".equals(kind)||"acompte".equals(kind)))||("payment".equals(filterKind)&&(signed>=0&&!"deposit".equals(kind)&&!"acompte".equals(kind)));if(!match)continue;String d=e.optString("operationDate",e.optString("date",""));if(period==1&&!today.equals(d))continue;if(period==2&&!d.startsWith(month))continue;if(period==3){try{Date x=new SimpleDateFormat("yyyy-MM-dd",Locale.FRANCE).parse(d);if(x==null||now-x.getTime()>30L*24*60*60*1000)continue;}catch(Exception ex){continue;}}if(period==4){java.time.YearMonth ym=java.time.YearMonth.now().minusMonths(1);if(!d.startsWith(ym.toString()))continue;}if(period==5){String y=String.valueOf(java.time.Year.now().getValue());if(!d.startsWith(y+"-"))continue;}if(period==6){String y=String.valueOf(java.time.Year.now().minusYears(1).getValue());if(!d.startsWith(y+"-"))continue;}String hay=(e.toString()).toLowerCase(Locale.ROOT);if(!q.isEmpty()&&!hay.contains(q))continue;out.put(e);}return out;}

    private void renderFinanceList(LinearLayout list,JSONArray source,String filterKind,String query,int period){list.removeAllViews();JSONArray rows=filteredAccounting(source,filterKind,query,period);if(rows.length()==0){list.addView(cardText("Aucun résultat","Aucun mouvement ne correspond aux filtres sélectionnés."));return;}double total=0;for(int i=0;i<rows.length();i++){JSONObject e=rows.optJSONObject(i);if(e==null)continue;double signed=signedAccountingAmount(e);total+=signed;String kind=e.optString("kind",e.optString("type","Mouvement"));LinearLayout c=card();c.addView(tv(e.optString("description",prettyKey(kind)),17,true,TEXT));c.addView(infoRow("Date",e.optString("operationDate",e.optString("date","—"))));c.addView(infoRow("Montant",moneySigned(signed)));String pm=e.optString("paymentMethod","");if(!pm.isEmpty())c.addView(infoRow("Règlement",prettyKey(pm)));String payer=e.optString("payerName","");if(!payer.isEmpty())c.addView(infoRow("Payeur / fournisseur",payer));String ref=e.optString("reference",e.optString("bookingId",""));if(!ref.isEmpty())c.addView(infoRow("Réservation / référence",ref));list.addView(c);}LinearLayout totalCard=card();totalCard.addView(infoRow("Total de la vue",moneySigned(total)));totalCard.addView(infoRow("Nombre d’écritures",String.valueOf(rows.length())));list.addView(totalCard,0);}

    private void launchAccountingCsvExport(JSONArray entries){StringBuilder sb=new StringBuilder();sb.append("date;type;description;montant;mode_paiement;reservation;payeur\n");for(int i=0;i<entries.length();i++){JSONObject e=entries.optJSONObject(i);if(e==null)continue;sb.append(csv(e.optString("operationDate",e.optString("date","")))).append(';').append(csv(e.optString("kind",e.optString("type","")))).append(';').append(csv(e.optString("description",""))).append(';').append(String.format(Locale.US,"%.2f",signedAccountingAmount(e))).append(';').append(csv(e.optString("paymentMethod",""))).append(';').append(csv(e.optString("reference",e.optString("bookingId","")))).append(';').append(csv(e.optString("payerName",""))).append('\n');}pendingAccountingCsv=sb.toString();Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/csv");i.putExtra(Intent.EXTRA_TITLE,"unite-paintball-comptabilite-"+new SimpleDateFormat("yyyy-MM-dd",Locale.FRANCE).format(new Date())+".csv");startActivityForResult(i,REQ_ACCOUNTING_EXPORT);}
    private String csv(String s){if(s==null)return "";String x=s.replace("\"","\"\"");return "\""+x+"\"";}

    private LinearLayout fieldWrap(String label, View field){LinearLayout w=card();w.addView(tv(label,14,true,TEXT));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(52));fp.setMargins(0,dp(8),0,0);w.addView(field,fp);return w;}

    private void loadPersonalFunds(){
        content.removeAllViews();adminSubpage=true;content.addView(sectionIntro("COMPTABILITÉ","Fonds à reverser","Encaissements personnels restant à reverser à l’association."));
        loadJson("/api/personal-funds",data->{int remaining=data.optInt("remainingCents",0);content.addView(cardText("Reste à reverser",String.format(Locale.FRANCE,"%.2f €",remaining/100.0)));JSONArray rows=data.optJSONArray("rows");if(rows==null||rows.length()==0)content.addView(cardText("À jour","Aucun encaissement personnel en attente."));else for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r==null)continue;LinearLayout c=card();c.addView(tv(r.optString("reference","Encaissement"),17,true,TEXT));c.addView(tv(r.optString("description",""),12,false,MUTED));c.addView(infoRow("Encaissé",String.format(Locale.FRANCE,"%.2f €",r.optInt("amount",0)*1.0)));c.addView(infoRow("Reste",String.format(Locale.FRANCE,"%.2f €",r.optInt("remainingCents",0)/100.0)));if(r.optInt("remainingCents",0)>0){Button reverse=button("Enregistrer un reversement");final String id=r.optString("id","");final int max=r.optInt("remainingCents",0);reverse.setOnClickListener(v->showPersonalRemittanceForm(id,max));c.addView(reverse);}content.addView(c);}Button b=secondaryButton("← Retour à la comptabilité");b.setOnClickListener(v->{section="Finance admin";render();});content.addView(b);});
    }

    private void showPersonalRemittanceForm(String entryId,int remainingCents){
        content.removeAllViews();adminSubpage=true;content.addView(sectionIntro("COMPTABILITÉ","Enregistrer un reversement","Le montant est déduit des fonds personnels restant à reverser."));EditText amount=editField("Montant en euros",String.format(Locale.FRANCE,"%.2f",remainingCents/100.0));amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);Spinner method=spinner(new String[]{"cash","association_transfer","other"});EditText date=editField("Date",new SimpleDateFormat("yyyy-MM-dd",Locale.FRANCE).format(new Date()));EditText note=editField("Note","");content.addView(amount);content.addView(fieldWrap("Mode",method));content.addView(date);content.addView(note);Button save=button("Valider le reversement");content.addView(save);save.setOnClickListener(v->{double a;try{a=Double.parseDouble(amount.getText().toString().trim().replace(',','.'));}catch(Exception e){Toast.makeText(this,"Montant invalide",Toast.LENGTH_LONG).show();return;}JSONObject b=new JSONObject();try{b.put("entryId",entryId);b.put("amount",a);b.put("method",String.valueOf(method.getSelectedItem()));b.put("operationDate",date.getText().toString().trim());b.put("note",note.getText().toString().trim());}catch(Exception ignored){}save.setEnabled(false);io.execute(()->{try{SyncApi.post(this,"/api/personal-funds",store.token(),b);runOnUiThread(()->{Toast.makeText(this,"Reversement enregistré",Toast.LENGTH_SHORT).show();loadPersonalFunds();});}catch(Exception e){runOnUiThread(()->{save.setEnabled(true);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});});Button back=secondaryButton("← Retour");back.setOnClickListener(v->loadPersonalFunds());content.addView(back);
    }

    private void renderStocksAndEquipment(){
        content.addView(sectionIntro("ADMINISTRATION","Stocks et matériel","Matériel réel synchronisé avec /api/safety : état, attribution et contrôles."));
        LinearLayout addCard=card();addCard.addView(tv("Ajouter un équipement",20,true,TEXT));EditText code=editField("Code / numéro","");EditText label=editField("Désignation","");Spinner category=spinner(new String[]{"lanceur","masque","bouteille","autre"});addCard.addView(code);addCard.addView(label);addCard.addView(fieldWrap("Catégorie",category));Button create=button("Ajouter au matériel");addCard.addView(create);content.addView(addCard);
        create.setOnClickListener(v->{if(code.getText().toString().trim().isEmpty()||label.getText().toString().trim().isEmpty()){Toast.makeText(this,"Code et désignation obligatoires",Toast.LENGTH_LONG).show();return;}JSONObject b=new JSONObject();try{b.put("action","equipment");b.put("code",code.getText().toString().trim());b.put("label",label.getText().toString().trim());b.put("category",String.valueOf(category.getSelectedItem()));}catch(Exception ignored){}create.setEnabled(false);io.execute(()->{try{SyncApi.post(this,"/api/safety",store.token(),b);runOnUiThread(()->{Toast.makeText(this,"Équipement ajouté",Toast.LENGTH_SHORT).show();content.removeAllViews();renderStocksAndEquipment();});}catch(Exception e){runOnUiThread(()->{create.setEnabled(true);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});});
        LinearLayout list=card();list.addView(tv("Inventaire matériel",20,true,TEXT));list.addView(tv("Chargement…",13,false,MUTED));content.addView(list);
        loadJson("/api/safety",data->{JSONArray equipment=data.optJSONArray("equipment");list.removeViews(1,Math.max(0,list.getChildCount()-1));if(equipment==null||equipment.length()==0){list.addView(tv("Aucun équipement enregistré.",13,false,MUTED));return;}for(int i=0;i<equipment.length();i++){JSONObject e=equipment.optJSONObject(i);if(e==null)continue;LinearLayout c=card();c.addView(tv(e.optString("label","Matériel"),17,true,TEXT));c.addView(infoRow("Code",e.optString("code","—")));c.addView(infoRow("Catégorie",prettyKey(e.optString("category","—"))));c.addView(infoRow("État",prettyKey(e.optString("condition","available"))));String assigned=e.optString("assignedUserId","");if(!assigned.isEmpty())c.addView(infoRow("Attribué à",assigned));String next=e.optString("nextCheckAt","");if(!next.isEmpty())c.addView(infoRow("Prochain contrôle",next));Button maintain=secondaryButton("Enregistrer un contrôle");final String equipmentId=e.optString("id","");maintain.setOnClickListener(v->showMaintenanceForm(equipmentId,e.optString("label","Matériel")));c.addView(maintain);list.addView(c);}});
    }

    private void showMaintenanceForm(String equipmentId,String label){
        content.removeAllViews();adminSubpage=true;content.addView(sectionIntro("MATÉRIEL","Contrôle · "+label,"Enregistre une maintenance directement dans le site."));EditText action=editField("Action","Contrôle visuel");EditText details=editField("Détails","");details.setMinLines(3);Spinner condition=spinner(new String[]{"available","maintenance","out_of_service","assigned"});EditText next=editField("Prochain contrôle (facultatif)","");content.addView(action);content.addView(details);content.addView(fieldWrap("État après contrôle",condition));content.addView(next);Button save=button("Enregistrer le contrôle");content.addView(save);save.setOnClickListener(v->{JSONObject b=new JSONObject();try{b.put("action","maintenance");b.put("equipmentId",equipmentId);b.put("maintenanceAction",action.getText().toString().trim());b.put("details",details.getText().toString().trim());b.put("condition",String.valueOf(condition.getSelectedItem()));b.put("nextCheckAt",next.getText().toString().trim());}catch(Exception ignored){}save.setEnabled(false);io.execute(()->{try{SyncApi.post(this,"/api/safety",store.token(),b);runOnUiThread(()->{Toast.makeText(this,"Contrôle enregistré",Toast.LENGTH_SHORT).show();section="Stocks";render();});}catch(Exception e){runOnUiThread(()->{save.setEnabled(true);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});});Button back=secondaryButton("← Retour au matériel");back.setOnClickListener(v->{section="Stocks";render();});content.addView(back);
    }

    private void renderIncidents(){
        content.addView(sectionIntro("ADMINISTRATION","Incidents et sécurité","Création et consultation des vraies fiches incident du site."));
        LinearLayout form=card();form.addView(tv("Nouvelle fiche incident",20,true,TEXT));EditText date=editField("Date",new SimpleDateFormat("yyyy-MM-dd",Locale.FRANCE).format(new Date()));EditText time=editField("Heure",new SimpleDateFormat("HH:mm",Locale.FRANCE).format(new Date()));EditText location=editField("Lieu","Terrain");EditText who=editField("Personnes concernées","");EditText desc=editField("Circonstances","");desc.setMinLines(3);EditText firstAid=editField("Premiers secours / mesures prises","");firstAid.setMinLines(2);EditText equipment=editField("Code matériel concerné (facultatif)","");EditText witnesses=editField("Témoins (facultatif)","");CheckBox emergency=acceptBox("Secours appelés");form.addView(date);form.addView(time);form.addView(location);form.addView(who);form.addView(desc);form.addView(firstAid);form.addView(equipment);form.addView(witnesses);form.addView(emergency);Button save=button("Enregistrer la fiche incident");form.addView(save);content.addView(form);
        save.setOnClickListener(v->{if(desc.getText().toString().trim().isEmpty()){Toast.makeText(this,"Décris les circonstances",Toast.LENGTH_LONG).show();return;}JSONObject b=new JSONObject();try{b.put("action","incident");b.put("date",normalizeBookingDate(date.getText().toString()));b.put("time",time.getText().toString().trim());b.put("location",location.getText().toString().trim());b.put("people",who.getText().toString().trim());b.put("circumstances",desc.getText().toString().trim());b.put("firstAid",firstAid.getText().toString().trim());b.put("equipmentCode",equipment.getText().toString().trim());b.put("witnesses",witnesses.getText().toString().trim());b.put("emergencyCalled",emergency.isChecked());}catch(Exception ignored){}save.setEnabled(false);io.execute(()->{try{SyncApi.post(this,"/api/safety",store.token(),b);runOnUiThread(()->{Toast.makeText(this,"Incident enregistré",Toast.LENGTH_SHORT).show();content.removeAllViews();renderIncidents();});}catch(Exception e){runOnUiThread(()->{save.setEnabled(true);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});});
        LinearLayout history=card();history.addView(tv("Historique des incidents",20,true,TEXT));history.addView(tv("Chargement…",13,false,MUTED));content.addView(history);loadJson("/api/safety",data->{JSONArray rows=data.optJSONArray("incidents");history.removeViews(1,Math.max(0,history.getChildCount()-1));if(rows==null||rows.length()==0){history.addView(tv("Aucun incident enregistré.",13,false,MUTED));return;}for(int i=0;i<Math.min(rows.length(),100);i++){JSONObject x=rows.optJSONObject(i);if(x==null)continue;LinearLayout c=card();c.addView(tv(x.optString("incidentDate","Incident")+" · "+x.optString("incidentTime",""),16,true,TEXT));c.addView(infoRow("Lieu",x.optString("location","—")));c.addView(infoRow("Personnes",x.optString("people","—")));c.addView(tv(x.optString("circumstances",""),13,false,MUTED));if(x.optBoolean("emergencyCalled",false))c.addView(miniPill("SECOURS APPELÉS",true));history.addView(c);}});
    }

    private void renderBeforeVisit(){
        content.addView(sectionIntro("PRÉPARATION","Avant de venir","Retrouve la checklist et les rappels importants avant ta partie."));
        LinearLayout c=card();c.addView(tv("À prévoir",24,true,TEXT));String[] x={"Chaussures fermées","Vêtements adaptés pouvant être salis","Bouteille d’eau","Pièce d’identité si demandée","Protections personnelles si souhaité"};for(String s:x){CheckBox cb=acceptBox(s);c.addView(cb);}content.addView(c);
        LinearLayout f=card();f.addView(tv("Fourni selon ta formule",22,true,TEXT));f.addView(tv("Masque, lanceur et équipement si la location est comprise ou choisie ; billes selon la formule réservée.",14,false,MUTED));Button r=secondaryButton("Ma réservation");r.setOnClickListener(v->{section="Réservations";render();});f.addView(r);content.addView(f);
        LinearLayout field=card();field.addView(tv("Sur le terrain",22,true,TEXT));field.addView(tv("Écoute les consignes météo et les instructions de l’arbitre. Le masque reste en place pendant le jeu et les zones interdites doivent être respectées.",14,false,MUTED));Button s=secondaryButton("Règles essentielles");s.setOnClickListener(v->{section="Sécurité";render();});field.addView(s);content.addView(field);
    }

    private void renderFriends(){
        content.addView(sectionIntro("COMMUNAUTÉ","Mes amis","Retrouve les joueurs ajoutés et contacte-les rapidement."));
        renderPlayers();
    }

    private void renderGroups(){
        content.addView(sectionIntro("COMMUNAUTÉ","Groupes et invitations","Crée une équipe, rejoins-la avec un code et retrouve ses membres et sa conversation."));

        LinearLayout tools=card();
        tools.addView(tv("Créer ou rejoindre une équipe",20,true,TEXT));
        EditText teamName=editField("Nom de la nouvelle équipe","");
        Button create=button("Créer mon équipe");
        tools.addView(teamName); tools.addView(create);
        EditText invite=editField("Code d’invitation (8 caractères)","");
        Button join=secondaryButton("Rejoindre avec le code");
        tools.addView(invite); tools.addView(join);
        content.addView(tools);

        LinearLayout list=card();
        list.addView(tv("Mes équipes",20,true,TEXT));
        list.addView(tv("Chargement…",13,false,MUTED));
        content.addView(list);

        create.setOnClickListener(v->{
            String name=teamName.getText().toString().trim();
            if(name.length()<2){Toast.makeText(this,"Indique un nom d’équipe",Toast.LENGTH_LONG).show();return;}
            JSONObject body=new JSONObject();try{body.put("action","create");body.put("name",name);}catch(Exception ignored){}
            create.setEnabled(false);io.execute(()->{try{JSONObject r=SyncApi.post(this,"/api/teams",store.token(),body);runOnUiThread(()->{create.setEnabled(true);teamName.setText("");Toast.makeText(this,"Équipe créée · code "+r.optString("inviteCode",""),Toast.LENGTH_LONG).show();content.removeAllViews();renderGroups();});}catch(Exception e){runOnUiThread(()->{create.setEnabled(true);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});
        });
        join.setOnClickListener(v->{
            String code=invite.getText().toString().trim().toUpperCase(Locale.FRANCE);
            if(code.length()<4){Toast.makeText(this,"Entre le code d’invitation",Toast.LENGTH_LONG).show();return;}
            JSONObject body=new JSONObject();try{body.put("action","join");body.put("inviteCode",code);}catch(Exception ignored){}
            join.setEnabled(false);io.execute(()->{try{SyncApi.post(this,"/api/teams",store.token(),body);runOnUiThread(()->{join.setEnabled(true);invite.setText("");Toast.makeText(this,"Équipe rejointe",Toast.LENGTH_SHORT).show();content.removeAllViews();renderGroups();});}catch(Exception e){runOnUiThread(()->{join.setEnabled(true);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});
        });

        loadJson("/api/teams",data->{
            list.removeViews(1,Math.max(0,list.getChildCount()-1));
            JSONArray teams=data.optJSONArray("teams");
            if(teams==null||teams.length()==0){list.addView(tv("Tu n’appartiens encore à aucune équipe. Crée-en une ou demande le code d’un capitaine.",13,false,MUTED));return;}
            for(int i=0;i<teams.length();i++){
                JSONObject t=teams.optJSONObject(i);if(t==null)continue;
                LinearLayout c=card();
                c.addView(tv(t.optString("name","Équipe"),19,true,TEXT));
                c.addView(infoRow("Rôle",t.optBoolean("mine",false)?"Capitaine":prettyKey(t.optString("myRole","membre"))));
                c.addView(infoRow("Code d’invitation",t.optString("inviteCode","—")));
                JSONArray members=t.optJSONArray("members");
                c.addView(infoRow("Membres",String.valueOf(members==null?0:members.length())));
                if(members!=null)for(int j=0;j<members.length();j++){JSONObject m=members.optJSONObject(j);if(m!=null)c.addView(tv("• "+m.optString("displayName","Joueur")+("captain".equals(m.optString("role"))?" · capitaine":""),13,false,MUTED));}
                Button chat=secondaryButton("Ouvrir la conversation d’équipe");final String teamId=t.optString("id","");final String teamLabel=t.optString("name","Équipe");chat.setOnClickListener(v->{activeChatChannel="team:"+teamId;activeChatLabel=teamLabel;section="Messages";render();});c.addView(chat);
                Button leave=secondaryButton(t.optBoolean("mine",false)?"Supprimer l’équipe si je suis seul":"Quitter l’équipe");leave.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Quitter l’équipe ?").setMessage("Les messages existants restent dans l’historique du site.").setNegativeButton("Annuler",null).setPositiveButton("Confirmer",(d,w)->{JSONObject body=new JSONObject();try{body.put("action","leave");body.put("teamId",teamId);}catch(Exception ignored){}io.execute(()->{try{SyncApi.post(this,"/api/teams",store.token(),body);runOnUiThread(()->{Toast.makeText(this,"Équipe mise à jour",Toast.LENGTH_SHORT).show();content.removeAllViews();renderGroups();});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}});}).show());c.addView(leave);
                list.addView(c);
            }
        });
    }

    private void renderSettings(){
        content.addView(sectionIntro("PARAMÈTRES","Paramètres et confidentialité","Réglages réellement appliqués au compte et à l’application Android."));

        LinearLayout notif=card(); notif.addView(tv("Notifications",21,true,TEXT)); notif.addView(tv("Choisis les catégories enregistrées côté serveur. Le son détaillé de chaque canal reste réglable dans Android.",13,false,MUTED));
        CheckBox booking=acceptBox("Réservations et confirmations"); CheckBox invitations=acceptBox("Invitations et groupes"); CheckBox messages=acceptBox("Messages"); CheckBox waitlist=acceptBox("Liste d’attente"); CheckBox marketing=acceptBox("Actualités / communication"); CheckBox push=acceptBox("Notifications push sur ce compte");
        notif.addView(booking);notif.addView(invitations);notif.addView(messages);notif.addView(waitlist);notif.addView(marketing);notif.addView(push);
        Button saveNotif=button("Enregistrer mes notifications"); notif.addView(saveNotif);
        Button androidNotif=secondaryButton("Réglages son et vibration Android"); notif.addView(androidNotif); content.addView(notif);
        androidNotif.setOnClickListener(v->{try{Intent i=new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);i.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName());startActivity(i);}catch(Exception e){Toast.makeText(this,"Réglages Android indisponibles",Toast.LENGTH_LONG).show();}});
        loadJson("/api/communication",data->{JSONObject p=data.optJSONObject("preferences");booking.setChecked(p==null||p.optBoolean("booking",true));invitations.setChecked(p==null||p.optBoolean("invitations",true));messages.setChecked(p==null||p.optBoolean("messages",true));waitlist.setChecked(p==null||p.optBoolean("waitlist",true));marketing.setChecked(p!=null&&p.optBoolean("marketing",false));push.setChecked(p==null||p.optBoolean("pushEnabled",true));});
        saveNotif.setOnClickListener(v->{JSONObject b=new JSONObject();try{b.put("action","preferences");b.put("booking",booking.isChecked());b.put("invitations",invitations.isChecked());b.put("messages",messages.isChecked());b.put("waitlist",waitlist.isChecked());b.put("marketing",marketing.isChecked());b.put("pushEnabled",push.isChecked());}catch(Exception ignored){}saveNotif.setEnabled(false);io.execute(()->{try{SyncApi.post(this,"/api/communication",store.token(),b);runOnUiThread(()->{saveNotif.setEnabled(true);Toast.makeText(this,"Préférences enregistrées",Toast.LENGTH_SHORT).show();});}catch(Exception e){runOnUiThread(()->{saveNotif.setEnabled(true);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});});

        LinearLayout sync=card(); sync.addView(tv("Synchronisation et hors-ligne",21,true,TEXT));
        Switch bgSync=new Switch(this);bgSync.setText(tr("Vérification en arrière-plan"));bgSync.setTextColor(TEXT);bgSync.setChecked(getSharedPreferences("up_settings",MODE_PRIVATE).getBoolean("background_sync",true));sync.addView(bgSync);
        bgSync.setOnCheckedChangeListener((b,checked)->{getSharedPreferences("up_settings",MODE_PRIVATE).edit().putBoolean("background_sync",checked).apply();BackgroundSyncScheduler.setEnabled(this,checked);});
        long cacheBytes=diskCache==null?0:diskCache.sizeBytes();sync.addView(infoRow("Cache local",String.format(Locale.FRANCE,"%.1f Mo",cacheBytes/1048576.0)));
        Button clear=secondaryButton("Vider le cache téléchargé");clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Vider le cache ?").setMessage("Les données pourront être retéléchargées. Les actions en attente de synchronisation ne sont pas supprimées.").setNegativeButton("Annuler",null).setPositiveButton("Vider",(d,w)->{if(diskCache!=null)diskCache.clear();Toast.makeText(this,"Cache vidé",Toast.LENGTH_SHORT).show();content.removeAllViews();renderSettings();}).show());sync.addView(clear);content.addView(sync);

        LinearLayout account=card();account.addView(tv("Compte et confidentialité",21,true,TEXT));
        Button profile=secondaryButton("Informations personnelles");profile.setOnClickListener(v->{section="Profil";render();});account.addView(profile);
        Button docs=secondaryButton("Consentements et documents");docs.setOnClickListener(v->{section="Documents";render();});account.addView(docs);
        Button export=secondaryButton("Télécharger mes données");export.setOnClickListener(v->downloadMyData());account.addView(export);
        content.addView(account);

        Button parityAudit=secondaryButton("Audit application / site");
        parityAudit.setOnClickListener(v->showParityAudit());
        content.addView(parityAudit);

        LinearLayout appearance=card();
        appearance.addView(tv("Langue et apparence",21,true,TEXT));
        appearance.addView(infoRow("Langue Android",AppUiPrefs.languageLabel(this)));
        Button changeLanguage=secondaryButton("Changer la langue");changeLanguage.setOnClickListener(v->showLanguageDialog());appearance.addView(changeLanguage);
        appearance.addView(infoRow("Thème",AppUiPrefs.themeLabel(this)));
        if(AppUiPrefs.followsSiteTheme(this)){
            long themeTs=SiteThemeManager.lastUpdatedAt(this);
            appearance.addView(infoRow("Source couleurs","Site Unité Paintball"));
            appearance.addView(infoRow("Dernière synchro thème",themeTs>0?new SimpleDateFormat("dd/MM · HH:mm",Locale.FRANCE).format(new Date(themeTs)):"En attente du site"));
        }
        Button changeTheme=secondaryButton("Changer le thème");changeTheme.setOnClickListener(v->showThemeDialog());appearance.addView(changeTheme);
        appearance.addView(infoRow("Push Firebase",FirebasePushManager.isConfigured(this)?tr("Configuré"):tr("À configurer")));
        appearance.addView(tv("La langue sélectionnée traduit la coque native, la navigation, les réglages et les libellés connus. Les textes métier dynamiques renvoyés par le site restent affichés tels que fournis par le serveur. En mode Site synchronisé, la palette active est récupérée depuis le site et conservée en cache pour le hors-ligne.",12,false,MUTED));
        content.addView(appearance);
    }


    private void showParityAudit(){
        String report="AUDIT INTERNE — APPLICATION / SITE\n\n"
                +"APP — OK / corrigé ici :\n"
                +"• cache-first + synchronisation de fond\n"
                +"• réservations 1 à 40 et rotations A/B\n"
                +"• likes/commentaires, QR, groupes, chat, retour Android\n"
                +"• menus repliables + recherche\n"
                +"• confirmations, modifications, nouveaux comptes/adhésions, restrictions, rôles, journal et paramètres branchés sur les contrats admin Work\n"
                +"• thème natif sombre/clair réellement commutable\n"
                +"• langue FR/EN/DE appliquée à la coque, navigation, réglages et libellés connus\n"
                +"• justificatifs comptables binaires téléchargés via la route dédiée\n"
                +"• accès direct au site pour les modules qui restent volontairement sans écriture native\n\n"
                +"APP — reste à valider / compléter :\n"
                +"• certains textes métier dynamiques restent dans la langue fournie par le serveur\n"
                +"• compilation APK/AAB et recette visuelle Android 13–16 sur appareils réels\n\n"
                +"SITE / API — reste à Work / environnement réel :\n"
                +"• participations : pas de contrat admin d’approbation dédié vérifié\n"
                +"• doubles comptes : aucune API de fusion automatique\n"
                +"• sauvegarde/restauration complète D1 + médias : indisponible\n"
                +"• statistiques/journal : lecture seule lorsqu’aucune écriture n’est nécessaire\n"
                +"• déployer et tester migrations, permissions et concurrence D1 sur l’environnement réel\n\n"
                +"EXTERNE : Firebase local = "+(FirebasePushManager.isConfigured(this)?"configuration détectée (push réel encore à tester)":"configuration absente")+". L’IA est volontairement hors périmètre de cette passe.\n\n"
                +"TESTS RÉELS : accueil/photos, réservation 1/20/21/30/40, anti-surbooking, messagerie, QR, hors-ligne, retour arrière, notifications, thème clair/sombre et langues FR/EN/DE.\n\n"
                +"Le dossier SUIVI-PROJET contient l’audit exact de la version 2.2.17.";
        showDocumentText("Audit application / site",report);
    }

    private void renderSafety(){
        content.addView(sectionIntro("CHARTE DE PARTICIPATION", "Charte de participation et de sécurité", "13 septembre 2026 — version 1"));
        Button open=secondaryButton("Ouvrir en grand"); content.addView(open);
        open.setOnClickListener(v -> Toast.makeText(this,"La charte est déjà affichée en plein écran dans l’application.",Toast.LENGTH_SHORT).show());

        LinearLayout charter=card();
        charter.setBackground(makeBg(CARD_2,18,ORANGE));
        addCharterSection(charter,"1. Objet et acceptation","La présente charte encadre l’accès au terrain et la participation aux activités organisées par l’association Unité Paintball à Pont-du-Navoy. Toute participation suppose une réservation acceptée, la lecture de cette charte, le briefing préalable et le respect permanent des consignes données par l’organisateur et les encadrants.");
        addCharterSection(charter,"2. Conditions de participation","Le participant déclare fournir des renseignements exacts, être en capacité de comprendre les consignes et adopter un comportement compatible avec une activité sportive de tir de loisir. L’organisateur peut refuser ou interrompre la participation d’une personne présentant un danger pour elle-même ou pour autrui, notamment en cas d’agressivité, d’intoxication, de refus des protections ou de non-respect répété des règles.");
        addCharterSection(charter,"3. Risques et santé","Le paintball comporte des risques inhérents, notamment impacts de billes, ecchymoses, chutes, glissades, collisions, obstacles, projections, bruit, fatigue et efforts physiques. Le participant certifie ne connaître aucune contre-indication médicale et s’engage à demander l’avis d’un professionnel de santé en cas de doute. Il signale avant la partie toute situation utile à sa sécurité sans être obligé de communiquer un diagnostic médical dans le formulaire.");
        addCharterSection(charter,"4. Protections et zones de jeu","• Le masque homologué et correctement ajusté reste porté dans toutes les zones où il est imposé. Il ne doit jamais être retiré, même en cas de buée ou d’arrêt momentané.\n\n• En zone neutre, le lanceur est sécurisé conformément au briefing : sécurité engagée, dispositif de canon installé et doigt hors détente.\n\n• Il est interdit de tirer hors des zones autorisées, à bout portant vers une personne non protégée, un animal, un véhicule, une habitation ou au-delà des limites du terrain.");
        addCharterSection(charter,"5. Matériel","Le matériel confié doit être vérifié visuellement à sa remise, utilisé uniquement selon les instructions, puis rendu complet. Toute anomalie doit être signalée immédiatement ; aucune réparation, modification ou démontage ne doit être entrepris par le participant. Une détérioration volontaire, un usage manifestement contraire aux consignes ou la non-restitution pourra engager la responsabilité de son auteur, hors usure normale et défaut préexistant.");
        addCharterSection(charter,"6. Comportement et substances","Les violences volontaires, menaces, insultes, tirs délibérément dangereux et dégradations sont interdits. La consommation d’alcool, de stupéfiants ou de toute substance altérant la vigilance est incompatible avec la participation. Le jeu doit cesser immédiatement au signal prévu par l’encadrement ou lorsqu’un joueur signale un problème de sécurité.");
        addCharterSection(charter,"7. Mineurs","La participation d’un mineur nécessite l’autorisation et la signature de son représentant légal, ainsi que le respect des conditions d’âge, d’équipement et d’encadrement fixées par l’organisateur et son assurance. Le représentant légal certifie l’exactitude des renseignements fournis et reste joignable pendant l’activité.");
        addCharterSection(charter,"8. Incident et premiers secours","Tout accident, douleur, malaise, perte de protection ou dommage matériel doit être signalé sans délai. Le participant accepte que l’organisateur interrompe son activité et contacte la personne d’urgence ou les secours lorsque la situation le justifie. Cette autorisation ne remplace pas le consentement médical exigé par la loi.");
        addCharterSection(charter,"9. Présence et confirmation","Le responsable du groupe s’engage à confirmer le nombre de participants et à répondre aux rappels WhatsApp. À défaut de réponse dans le délai indiqué, le créneau pourra être libéré. Une absence injustifiée peut être enregistrée afin de soumettre une future demande à une validation manuelle. Le règlement est effectué uniquement sur place.");
        addCharterSection(charter,"10. Droit à l’image","La publication d’une photographie ou d’une vidéo identifiable repose sur une autorisation distincte et facultative. Refuser cette autorisation n’empêche pas de participer. Une autorisation donnée peut être retirée pour l’avenir en contactant l’association, sans remettre en cause les utilisations licites déjà réalisées.");
        addCharterSection(charter,"11. Données personnelles et identité","Les données demandées servent à gérer la réservation, la sécurité, les contacts d’urgence, les confirmations et la preuve des engagements. Elles sont accessibles uniquement aux personnes autorisées et conservées pendant la durée nécessaire à ces finalités ; la charte signée peut être conservée jusqu’à cinq ans après l’activité, davantage seulement en cas de litige. Le participant peut demander l’accès, la rectification ou l’effacement de ses données auprès d’Unité Paintball. Une pièce d’identité peut être vérifiée sur place, mais son original ou sa photographie ne sont pas conservés.");
        addCharterSection(charter,"12. Portée juridique","Cette charte établit l’information reçue et les engagements personnels du participant. Elle ne constitue ni une renonciation générale à recours, ni une exclusion des obligations de sécurité ou des responsabilités que la loi interdit d’écarter. Chaque personne reste responsable de ses fautes personnelles dans les conditions prévues par le droit français. Si une disposition est déclarée inapplicable, les autres restent en vigueur.");
        content.addView(charter);

        LinearLayout signature=card(); signature.setBackground(makeBg(CARD_2,18,ORANGE));
        signature.addView(tv("13. Signature électronique",22,true,NeonPalette.CYAN));
        TextView expl=tv("En cochant les acceptations obligatoires puis en inscrivant son nom complet, le signataire reconnaît avoir lu la version indiquée, avoir pu poser ses questions et accepter la charte avant l’envoi. La date, l’heure, la version de la charte et les renseignements associés à la réservation sont enregistrés.",14,false,MUTED); expl.setPadding(0,dp(10),0,dp(12)); signature.addView(expl);
        CheckBox a1=acceptBox("J’ai lu et j’accepte le règlement et toutes les consignes de sécurité. *");
        CheckBox a2=acceptBox("Je reconnais avoir été informé des risques inhérents à la pratique du paintball. *");
        CheckBox a3=acceptBox("Je certifie être apte à participer et ne pas avoir de contre-indication médicale connue. *");
        CheckBox img=acceptBox("J’autorise gratuitement la publication de photos ou vidéos où je peux apparaître. Facultatif.");
        CheckBox full=acceptBox("Je reconnais avoir lu intégralement la charte du 13 septembre 2026 — version 1, avoir compris ses règles et les accepter sans réserve, sous réserve des droits auxquels la loi interdit de renoncer. *");
        signature.addView(a1); signature.addView(a2); signature.addView(a3); signature.addView(img); signature.addView(full);
        content.addView(signature);

        LinearLayout sport=cardText("Garantie complémentaire Sport+ — choix obligatoire","Dans le cadre du dossier d’assurance sportive de l’association, indiquez si vous souhaitez souscrire cette garantie complémentaire.");
        Spinner insurance=spinner(new String[]{"Je refuse la garantie complémentaire","Je souhaite souscrire la garantie complémentaire"}); sport.addView(insurance); content.addView(sport);
    }

    private void addCharterSection(LinearLayout parent,String heading,String body){
        TextView h=tv(heading,20,true,TEXT); h.setPadding(0,dp(16),0,dp(8)); parent.addView(h);
        TextView b=tv(body,14,false,MUTED); b.setLineSpacing(0,1.22f); parent.addView(b);
    }

    private void renderScenarios(){
        content.addView(sectionIntro("JEUX", "Jeux et scénarios", "Photos, objectifs, règles et préparation de chaque mode."));
        loadJson("/api/scenarios", data -> {
            JSONArray rows=extractArray(data,"scenarios","items","rows");
            if(rows==null||rows.length()==0){content.addView(cardText("Aucun scénario publié","Le catalogue est indisponible."));return;}
            for(int i=0;i<rows.length();i++){
                JSONObject x=rows.optJSONObject(i);if(x==null)continue;
                LinearLayout c=card();String image=x.optString("imageUrl");
                if(!image.isEmpty()){ImageView photo=new ImageView(this);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);photo.setBackground(makeBg(CARD_2,18,STROKE));photo.setClipToOutline(true);c.addView(photo,new LinearLayout.LayoutParams(-1,dp(185)));loadRemoteImage(photo,image);}
                c.addView(tv(x.optString("name","Scénario"),21,true,TEXT));
                c.addView(tv(x.optString("objective",x.optString("description")),14,false,MUTED));
                c.addView(tv(x.optString("category")+"  ·  "+x.optInt("duration")+" min  ·  "+x.optString("players"),12,true,ORANGE));
                LinearLayout details=new LinearLayout(this);details.setOrientation(LinearLayout.VERTICAL);details.setVisibility(View.GONE);
                for(String[] field:new String[][]{{"objective","Objectif et victoire"},{"rules","Règles du scénario"},{"briefing","Déroulement de la manche"},{"equipment","Matériel nécessaire"},{"difficulty","Difficulté"},{"variants","Variantes"},{"safety","Sécurité et arbitrage"}}){String value=x.optString(field[0]);if(!value.isEmpty()){details.addView(tv(field[1],16,true,TEXT));details.addView(tv(value,14,false,MUTED));}}
                Button more=secondaryButton("Voir la fiche complète");more.setOnClickListener(v->{boolean open=details.getVisibility()!=View.VISIBLE;details.setVisibility(open?View.VISIBLE:View.GONE);more.setText(open?"Réduire la fiche":"Voir la fiche complète");});c.addView(more);c.addView(details);content.addView(c);
            }
        });
    }

    private void renderAiAssistant(){
        content.addView(sectionIntro("ASSISTANT IA", "Assistant Unité Paintball", "Pose tes questions sur les réservations, la préparation, la sécurité, les scénarios ou le fonctionnement du terrain."));
        LinearLayout info=card();info.addView(tv("✦ Une aide rapide, pas un remplacement de l’équipe",18,true,LIME));info.addView(tv("L’assistant peut expliquer et orienter. Pour une disponibilité, un paiement, une urgence ou une décision administrative, les données du site et l’équipe restent prioritaires.",13,false,MUTED));content.addView(info);
        LinearLayout suggestions=row();
        Button s1=secondaryButton("Que prévoir ?");Button s2=secondaryButton("Règles sécurité");Button s3=secondaryButton("Comment réserver ?");
        suggestions.addView(s1,new LinearLayout.LayoutParams(0,dp(46),1));LinearLayout.LayoutParams sp2=new LinearLayout.LayoutParams(0,dp(46),1);sp2.setMargins(dp(5),0,dp(5),0);suggestions.addView(s2,sp2);suggestions.addView(s3,new LinearLayout.LayoutParams(0,dp(46),1));content.addView(suggestions);
        LinearLayout chat=card();chat.addView(tv("Conversation",19,true,TEXT));LinearLayout log=new LinearLayout(this);log.setOrientation(LinearLayout.VERTICAL);chat.addView(log);
        log.addView(aiBubble("Bonjour ! Je suis l’assistant Unité Paintball. Que veux-tu savoir ?",false));
        EditText question=editField("Écris ta question…","");question.setSingleLine(false);question.setMinLines(2);chat.addView(question);
        Button send=button("✦ Demander à l’assistant");chat.addView(send);content.addView(chat);
        final JSONArray history=new JSONArray();
        View.OnClickListener submit=v->{String q=question.getText().toString().trim();if(q.isEmpty())return;question.setText("");log.addView(aiBubble(q,true));send.setEnabled(false);send.setText("Réponse en cours…");try{history.put(new JSONObject().put("role","user").put("content",q));}catch(Exception ignored){}
            io.execute(()->{try{JSONObject body=new JSONObject().put("message",q).put("history",history);JSONObject r=ApiClient.post("/api/ai-assistant",store.token(),body);String answer=r.optString("answer","Je n’ai pas pu répondre pour le moment.");try{history.put(new JSONObject().put("role","assistant").put("content",answer));}catch(Exception ignored){}runOnUiThread(()->{log.addView(aiBubble(answer,false));send.setEnabled(true);send.setText("✦ Demander à l’assistant");});}catch(Exception e){runOnUiThread(()->{log.addView(aiBubble("Assistant indisponible pour le moment. Tu peux utiliser Aide et assistance pour contacter l’équipe.\n\n"+(e.getMessage()==null?"":e.getMessage()),false));send.setEnabled(true);send.setText("✦ Demander à l’assistant");});}});};
        send.setOnClickListener(submit);
        s1.setOnClickListener(v->{question.setText("Que dois-je prévoir avant de venir jouer au paintball ?");submit.onClick(v);});
        s2.setOnClickListener(v->{question.setText("Quelles sont les principales règles de sécurité ?");submit.onClick(v);});
        s3.setOnClickListener(v->{question.setText("Comment faire une réservation ?");submit.onClick(v);});
    }

    private LinearLayout aiBubble(String message,boolean mine){
        LinearLayout wrap=new LinearLayout(this);wrap.setGravity(mine?Gravity.RIGHT:Gravity.LEFT);wrap.setPadding(0,dp(5),0,dp(5));
        TextView bubble=tv(message,14,false,mine?BG:TEXT);bubble.setPadding(dp(13),dp(11),dp(13),dp(11));bubble.setBackground(makeBg(mine?LIME:CARD_2,16,mine?LIME:STROKE));bubble.setMaxWidth((int)(getResources().getDisplayMetrics().widthPixels*.72f));wrap.addView(bubble);return wrap;
    }

    private void renderHelp(){
        content.addView(sectionIntro("ASSISTANCE", "Aide et assistance", "Consulte les raccourcis utiles ou envoie une demande au support Unité Paintball."));
        LinearLayout quick=card();quick.addView(tv("Raccourcis",20,true,TEXT));
        Button docs=secondaryButton("Documents et autorisations");docs.setOnClickListener(v->{section="Documents";render();});quick.addView(docs);
        Button safety=secondaryButton("Consignes de sécurité");safety.setOnClickListener(v->{section="Sécurité";render();});quick.addView(safety);
        Button messages=secondaryButton("Messagerie");messages.setOnClickListener(v->{section="Messages";render();});quick.addView(messages);content.addView(quick);

        JSONObject user=snapshot==null?null:snapshot.optJSONObject("user");JSONObject profile=snapshot==null?null:snapshot.optJSONObject("profile");
        LinearLayout form=card();form.addView(tv("Contacter l’association",20,true,TEXT));
        EditText name=editField("Nom et prénom",profile==null?"":join(profile.optString("firstName"),profile.optString("lastName")));
        EditText email=editField("E-mail",user==null?"":user.optString("email",""));
        Spinner category=spinner(new String[]{"Question","Réservation","Compte","Paiement","Problème technique","Autre"});
        EditText subject=editField("Sujet","");EditText description=editField("Décris ta demande","");description.setMinLines(4);
        form.addView(name);form.addView(email);form.addView(fieldWrap("Catégorie",category));form.addView(subject);form.addView(description);
        Button send=button("Envoyer la demande");form.addView(send);content.addView(form);
        send.setOnClickListener(v->{
            if(name.getText().toString().trim().isEmpty()||!email.getText().toString().contains("@")||subject.getText().toString().trim().isEmpty()||description.getText().toString().trim().length()<10){Toast.makeText(this,"Complète les informations et décris le problème.",Toast.LENGTH_LONG).show();return;}
            JSONObject body=new JSONObject();try{body.put("name",name.getText().toString().trim());body.put("email",email.getText().toString().trim());body.put("category",String.valueOf(category.getSelectedItem()));body.put("subject",subject.getText().toString().trim());body.put("description",description.getText().toString().trim());body.put("pageUrl","android://help");}catch(Exception ignored){}
            send.setEnabled(false);io.execute(()->{try{JSONObject r=SyncApi.post(this,"/api/support",store.token(),body);runOnUiThread(()->{Toast.makeText(this,r.optBoolean("_queued",false)?"Demande conservée hors ligne, envoi automatique à la reconnexion":"Demande envoyée · "+r.optString("reference","support"),Toast.LENGTH_LONG).show();send.setEnabled(true);subject.setText("");description.setText("");});}catch(Exception e){runOnUiThread(()->{send.setEnabled(true);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});
        });
    }


    /**
     * 2.2.31 — Accueil simplifié : une seule action principale, l'état de la prochaine partie,
     * la progression utile et quatre raccourcis contextuels. Le fil social vient ensuite.
     */
    private boolean avatarPromptChecked=false;
    private void promptAvatarOnce(){if(avatarPromptChecked||store==null||store.token().isEmpty())return;avatarPromptChecked=true;io.execute(()->{try{JSONObject data=ApiClient.post("/api/avatar",store.token(),new JSONObject().put("action","prompt"));if(data.optBoolean("show"))runOnUiThread(()->{if(!isFinishing())new android.app.AlertDialog.Builder(this).setTitle("Crée ton joueur Unité").setMessage("Ta tenue de départ est offerte. Ton avatar restera modifiable dans Profil > Mon avatar.").setPositiveButton("Créer",(d,w)->startActivity(new Intent(this,SoldierActivity.class))).setNegativeButton("Plus tard",null).show();});}catch(Exception ignored){}});}
    private void renderHome(){promptAvatarOnce();
        JSONObject p=snapshot.optJSONObject("profile");
        String first=p==null?"Joueur":p.optString("firstName",p.optString("nickname","Joueur"));
        if(first==null||first.trim().isEmpty())first="Joueur";

        // 2.2.35 — HOME LEGACY REMASTER : structure de l'ancienne home appréciée,
        // sans perdre les fonctions récentes. Réservation et QR d'abord, actualités juste après.
        LinearLayout welcome=new LinearLayout(this);welcome.setOrientation(LinearLayout.VERTICAL);welcome.setPadding(dp(4),dp(6),dp(4),dp(4));
        TextView hello=tv("Salut "+first,22,true,TEXT);welcome.addView(hello);
        welcome.addView(tv("Réserve, retrouve ton QR et suis directement la vie de l’Unité.",12,false,MUTED));
        content.addView(welcome);

        LinearLayout legacyRow=row();legacyRow.setGravity(Gravity.TOP);
        LinearLayout reserveTile=legacyHomeActionTile("▣","Réserver","Choisir une date",true,NeonPalette.CYAN);
        reserveTile.setOnClickListener(v->{section="Réservations";render();});
        LinearLayout qrTile=legacyQrTile();qrTile.setOnClickListener(v->{section="QR";render();});
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(184),1);a.setMargins(dp(1),dp(6),dp(6),dp(6));legacyRow.addView(reserveTile,a);
        LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,dp(184),1);b.setMargins(dp(6),dp(6),dp(1),dp(6));legacyRow.addView(qrTile,b);
        content.addView(legacyRow);

        Button openDays=secondaryButton("♙  VOIR LES JOURNÉES OUVERTES");
        openDays.setOnClickListener(v->{section="Parties ouvertes";render();});
        LinearLayout.LayoutParams odp=new LinearLayout.LayoutParams(-1,dp(48));odp.setMargins(dp(2),dp(2),dp(2),dp(8));content.addView(openDays,odp);

        JSONObject next=snapshot.optJSONObject("next");
        if(next!=null){
            LinearLayout n=card();n.setBackground(makeBg(CARD_2,18,NeonPalette.GREEN));
            LinearLayout nh=row();nh.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout nc=new LinearLayout(this);nc.setOrientation(LinearLayout.VERTICAL);
            nc.addView(tv("PROCHAINE PARTIE",10,true,NeonPalette.GREEN));
            nc.addView(tv(next.optString("title","Unité Paintball"),18,true,TEXT));
            nc.addView(tv(next.optString("date","—")+" · "+next.optString("time","—")+" · "+next.optInt("players",1)+" joueur(s)",12,false,MUTED));
            nh.addView(nc,new LinearLayout.LayoutParams(0,-2,1));
            Button details=secondaryButton("DÉTAILS");details.setOnClickListener(v->{section="Espace joueur";render();});nh.addView(details,new LinearLayout.LayoutParams(dp(104),dp(44)));
            n.addView(nh);content.addView(n);
        }

        // Composeur toujours visible : aucune recherche dans Profil/Galerie pour publier.
        LinearLayout composer=card();composer.setBackground(makeBg(CARD_2,18,NeonPalette.CYAN));
        LinearLayout composerTop=row();
        TextView av=tv(initials(first),13,true,BG);av.setGravity(Gravity.CENTER);av.setBackground(makeBg(NeonPalette.GREEN,999,NeonPalette.GREEN));composerTop.addView(av,new LinearLayout.LayoutParams(dp(42),dp(42)));
        TextView prompt=tv("Publier dans le fil de l’Unité…",13,false,MUTED);prompt.setGravity(Gravity.CENTER_VERTICAL);prompt.setPadding(dp(13),0,dp(8),0);prompt.setBackground(makeBg(BG,999,STROKE));LinearLayout.LayoutParams plp=new LinearLayout.LayoutParams(0,dp(42),1);plp.setMargins(dp(8),0,0,0);composerTop.addView(prompt,plp);composer.addView(composerTop);
        // Actions du composeur : disposition mobile 2 + 1 pour éviter que « ÉVÉNEMENT »
        // soit tronqué sur les écrans étroits. Les deux premières actions partagent la
        // première ligne ; ÉVÉNEMENT occupe toute la largeur sur la seconde ligne.
        LinearLayout publishTop=row();
        Button photo=secondaryButton("▧ PHOTO");photo.setOnClickListener(v->openGalleryPicker(false));
        Button video=secondaryButton("▶ VIDÉO");video.setOnClickListener(v->openGalleryPicker(true));
        Button event=secondaryButton("◉ ÉVÉNEMENT");event.setOnClickListener(v->{section="Événements";render();});
        publishTop.addView(photo,new LinearLayout.LayoutParams(0,dp(44),1));
        LinearLayout.LayoutParams p2=new LinearLayout.LayoutParams(0,dp(44),1);p2.setMargins(dp(6),0,0,0);publishTop.addView(video,p2);
        composer.addView(publishTop);
        LinearLayout.LayoutParams eventLp=new LinearLayout.LayoutParams(-1,dp(44));eventLp.setMargins(0,dp(6),0,0);composer.addView(event,eventLp);
        content.addView(composer);

        LinearLayout feedHead=row();feedHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView feedTitle=tv("FIL D’ACTUALITÉ",13,true,NeonPalette.CYAN_BRIGHT);feedTitle.setLetterSpacing(.12f);feedHead.addView(feedTitle,new LinearLayout.LayoutParams(0,dp(40),1));
        Button allFeed=secondaryButton("TOUT VOIR");allFeed.setOnClickListener(v->{section="Actualités";render();});feedHead.addView(allFeed,new LinearLayout.LayoutParams(dp(108),dp(40)));content.addView(feedHead);
        LinearLayout feedHost=new LinearLayout(this);feedHost.setOrientation(LinearLayout.VERTICAL);content.addView(feedHost);
        LinearLayout feedLoading=card();feedLoading.addView(tv("Chargement des publications…",13,false,MUTED));feedHost.addView(feedLoading);
        loadJson("/api/content", data -> {
            feedHost.removeAllViews();JSONArray galleryArr=data.optJSONArray("gallery");JSONArray events=data.optJSONArray("events");int shown=0;
            if(galleryArr!=null)for(int i=0;i<Math.min(galleryArr.length(),10);i++){JSONObject g=galleryArr.optJSONObject(i);if(g!=null){feedHost.addView(buildHomeSocialPost(g,i==0));shown++;}}
            if(events!=null&&events.length()>0){LinearLayout ec=card();ec.setBackground(makeBg(CARD_2,18,NeonPalette.GREEN));ec.addView(tv("À VENIR",11,true,NeonPalette.GREEN));for(int i=0;i<Math.min(2,events.length());i++){JSONObject e=events.optJSONObject(i);if(e==null)continue;ec.addView(tv(e.optString("title",e.optString("name","Événement")),17,true,TEXT));ec.addView(tv(e.optString("date",e.optString("startsAt","")),12,false,MUTED));}ec.setOnClickListener(v->{section="Événements";render();});feedHost.addView(ec);shown++;}
            if(shown==0)feedHost.addView(cardText("Aucune publication pour le moment","Les nouvelles photos, vidéos et informations du terrain apparaîtront ici."));
        });

        // Accès secondaires conservés mais déplacés après le fil pour ne plus étouffer l'accueil.
        HorizontalScrollView quickScroll=new HorizontalScrollView(this);quickScroll.setHorizontalScrollBarEnabled(false);quickScroll.setClipToPadding(false);
        LinearLayout quickRow=new LinearLayout(this);quickRow.setOrientation(LinearLayout.HORIZONTAL);quickRow.setPadding(0,dp(5),0,dp(8));
        String[][] shortcuts={{"♙","Mes amis","Amis"},{"⚔","Arena","Arena"},{"🎯","Récompenses","Points"},{"▧","Galerie","Galerie"},{"✉","Messages","Messages"}};
        for(String[] x:shortcuts){LinearLayout q=socialStory(x[0],x[1]);q.setOnClickListener(v->{section=x[2];render();});quickRow.addView(q);}quickScroll.addView(quickRow);content.addView(quickScroll);

        LinearLayout progressionHost=new LinearLayout(this);progressionHost.setOrientation(LinearLayout.VERTICAL);content.addView(progressionHost);loadHomeProgressionSummary(progressionHost);
    }

    private LinearLayout legacyHomeActionTile(String icon,String heading,String sub,boolean bright,int accent){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.BOTTOM|Gravity.LEFT);c.setPadding(dp(17),dp(15),dp(17),dp(17));
        if(bright){GradientDrawable gd=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{NeonPalette.CYAN,NeonPalette.GREEN});gd.setCornerRadius(dp(20));gd.setStroke(dp(1),NeonPalette.CYAN_BRIGHT);c.setBackground(gd);}else c.setBackground(makeBg(CARD_2,20,accent));
        int tc=bright?Color.rgb(4,20,18):TEXT, sc=bright?Color.argb(170,4,20,18):MUTED;
        c.addView(tv(icon,34,true,tc));TextView h=tv(heading,20,true,tc);h.setPadding(0,dp(8),0,0);c.addView(h);c.addView(tv(sub,12,false,sc));return c;
    }

    private LinearLayout legacyQrTile(){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.BOTTOM|Gravity.LEFT);c.setPadding(dp(17),dp(12),dp(17),dp(17));c.setBackground(makeBg(CARD_2,20,NeonPalette.CYAN));
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.unite_paintball_logo);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);c.addView(logo,new LinearLayout.LayoutParams(-1,dp(82)));
        TextView h=tv("Mon QR code",19,true,TEXT);h.setPadding(0,dp(4),0,0);c.addView(h);c.addView(tv("Accès rapide",12,false,MUTED));return c;
    }

    private LinearLayout uxTile(String icon,String heading,String sub,String target,int accent){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(14),dp(14),dp(14));c.setBackground(makeBg(CARD_2,16,accent));
        TextView i=tv(icon,22,true,accent);c.addView(i);TextView h=tv(heading,16,true,TEXT);h.setPadding(0,dp(6),0,0);c.addView(h);c.addView(tv(sub,11,false,MUTED));
        c.setOnClickListener(v->{section=target;render();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(112),1);lp.setMargins(dp(4),dp(5),dp(4),dp(5));c.setLayoutParams(lp);return c;
    }

    private void loadHomeProgressionSummary(LinearLayout host){
        host.removeAllViews();LinearLayout loading=card();loading.addView(tv("PROGRESSION",11,true,NeonPalette.GREEN));loading.addView(tv("Synchronisation de ton niveau…",12,false,MUTED));host.addView(loading);
        loadJson("/api/gamification?scope=profile", data->{
            host.removeAllViews();JSONObject progression=data.optJSONObject("progression"),level=data.optJSONObject("level"),rank=data.optJSONObject("rank"),daily=data.optJSONObject("dailyRoulette");
            if(progression==null){host.addView(cardText("Progression","Disponible dès que le serveur Gamification est à jour."));return;}
            LinearLayout c=card();c.setBackground(makeBg(CARD_2,18,NeonPalette.GREEN));
            int lv=level==null?progression.optInt("level",1):level.optInt("level",1);String rankText=rank==null?progression.optString("rankCode","Recrue"):rank.optString("name","Recrue")+" "+rank.optString("division","");
            c.addView(tv("PROGRESSION UNITÉ",11,true,NeonPalette.GREEN));c.addView(tv("Niveau "+lv+" · "+rankText,22,true,TEXT));
            int current=level==null?0:level.optInt("currentXp",0),next=Math.max(1,level==null?1000:level.optInt("nextXp",1000));ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(next);bar.setProgress(Math.min(current,next));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(NeonPalette.CYAN));c.addView(tv(current+" / "+next+" XP",12,false,MUTED));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(9));bp.setMargins(0,dp(7),0,dp(8));c.addView(bar,bp);
            LinearLayout stats=row();stats.addView(stat("Points",String.valueOf(progression.optInt("unityPoints",0))),weight());stats.addView(stat("Rang",rankText),weight());stats.addView(stat("Roulette",daily!=null&&daily.optBoolean("available",false)?"Prête":"Demain"),weight());c.addView(stats);
            Button open=secondaryButton("VOIR MA PROGRESSION");open.setOnClickListener(v->{section="Points";render();});c.addView(open);host.addView(c);
        });
    }

    /** Hub Jouer : toutes les actions terrain dans un seul écran. */
    private void renderPlayHub(){
        content.addView(sectionIntro("JOUER","Prêt pour le terrain ?","Réserve ta partie ou rejoins une journée où des joueurs sont déjà inscrits."));

        LinearLayout booking=card();booking.setBackground(makeBg(CARD_2,20,NeonPalette.CYAN));
        booking.addView(tv("RÉSERVATION",11,true,NeonPalette.CYAN_BRIGHT));
        booking.addView(tv("Choisis ta date et prépare ta partie",24,true,TEXT));
        booking.addView(tv("Formule, participants, horaire et options : tout se fait ici.",13,false,MUTED));
        Button reserve=button("＋ RÉSERVER UNE PARTIE");reserve.setTextSize(16);reserve.setOnClickListener(v->{section="Réservations";render();});booking.addView(reserve);content.addView(booking);

        Button liveNow=secondaryButton("● LIVE GAME · PARTIE EN DIRECT");
        liveNow.setOnClickListener(v->{section="Live";render();});
        LinearLayout.LayoutParams liveNowParams=new LinearLayout.LayoutParams(-1,dp(54));liveNowParams.setMargins(0,0,0,dp(12));
        content.addView(liveNow,liveNowParams);

        JSONObject next=snapshot.optJSONObject("next");
        if(next!=null){
            LinearLayout action=card();action.setBackground(makeBg(CARD_2,18,NeonPalette.GREEN));action.addView(tv("TA PROCHAINE PARTIE",11,true,NeonPalette.GREEN));action.addView(tv(next.optString("date","Partie")+" · "+next.optString("time",""),22,true,TEXT));
            LinearLayout r=row();Button qr=button("AFFICHER MON QR");qr.setOnClickListener(v->{section="QR";render();});Button details=secondaryButton("DÉTAILS");details.setOnClickListener(v->{section="Espace joueur";render();});r.addView(qr,new LinearLayout.LayoutParams(0,dp(50),1));LinearLayout.LayoutParams dp2=new LinearLayout.LayoutParams(0,dp(50),1);dp2.setMargins(dp(8),0,0,0);r.addView(details,dp2);action.addView(r);content.addView(action);
        }

        LinearLayout openPreview=new LinearLayout(this);openPreview.setOrientation(LinearLayout.VERTICAL);content.addView(openPreview);loadOpenGamesPreview(openPreview,4);

        LinearLayout social=card();social.addView(tv("COMMUNAUTÉ",11,true,NeonPalette.CYAN));social.addView(tv("Tes amis et les actualités restent à portée de main",20,true,TEXT));
        LinearLayout sr=row();sr.addView(uxTile("♙","Mes amis","Contacts joueurs","Amis",NeonPalette.CYAN),weight());sr.addView(uxTile("▧","Actualités & photos","Fil communautaire","Actualités",NeonPalette.GREEN),weight());social.addView(sr);content.addView(social);

        LinearLayout grid=card();grid.addView(tv("OUTILS TERRAIN",11,true,NeonPalette.CYAN));
        LinearLayout r1=row();r1.addView(uxTile("⌁","Mon QR","Arrivée terrain","QR",NeonPalette.GREEN),weight());r1.addView(uxTile("●","Live","Partie en direct","Live",NeonPalette.CYAN),weight());grid.addView(r1);
        LinearLayout r2=row();r2.addView(uxTile("♛","Équipe","Groupes & invitations","Groupes",NeonPalette.CYAN_BRIGHT),weight());r2.addView(uxTile("✓","Avant de venir","Checklist & sécurité","Avant de venir",NeonPalette.GREEN),weight());grid.addView(r2);
        LinearLayout r3=row();r3.addView(uxTile("♟","Événements","Événements officiels","Événements",NeonPalette.CYAN),weight());r3.addView(uxTile("⌖","Scénarios","Missions paintball","Scénarios",NeonPalette.GREEN),weight());grid.addView(r3);content.addView(grid);

        LinearLayout services=card();services.addView(tv("TOUS LES SERVICES",11,true,NeonPalette.CYAN));
        LinearLayout s1=row();s1.addView(uxTile("✉","Messages","Conversations privées","Messages",NeonPalette.CYAN_BRIGHT),weight());s1.addView(uxTile("●","Notifications","Alertes & actions","Notifications",NeonPalette.GREEN),weight());services.addView(s1);
        LinearLayout s2=row();s2.addView(uxTile("▧","Galerie","Photos & vidéos","Galerie",NeonPalette.CYAN),weight());s2.addView(uxTile("♛","Tournois","Compétitions","Tournois",NeonPalette.GREEN),weight());services.addView(s2);
        LinearLayout s3=row();s3.addView(uxTile("▤","Documents","Chartes & autorisations","Documents",NeonPalette.CYAN),weight());s3.addView(uxTile("€","Paiements","Acomptes & solde","Paiements",NeonPalette.GREEN),weight());services.addView(s3);
        LinearLayout s4=row();s4.addView(uxTile("★","Adhésion","Statut & demande","Adhésions",NeonPalette.CYAN_BRIGHT),weight());s4.addView(uxTile("⌛","Liste d’attente","Places libérées","Attente",NeonPalette.GREEN),weight());services.addView(s4);
        content.addView(services);
    }

    private void loadOpenGamesPreview(LinearLayout host,int limit){
        host.removeAllViews();
        LinearLayout loading=card();loading.addView(tv("JOURNÉES OUVERTES",11,true,NeonPalette.GREEN));loading.addView(tv("Recherche des événements et parties que tu peux rejoindre…",13,false,MUTED));host.addView(loading);
        loadUnifiedOpenSessions(host,limit,false);
    }

    private void renderOpenGames(){
        content.addView(sectionIntro("JOUER ENSEMBLE","Journées ouvertes","Événements publics, terrain ouvert et parties déjà lancées : rejoins les joueurs présents ou réserve sur la même journée."));
        LinearLayout reserve=card();reserve.setBackground(makeBg(CARD_2,18,NeonPalette.CYAN));reserve.addView(tv("TU PRÉFÈRES CRÉER TA PROPRE RÉSERVATION ?",11,true,NeonPalette.CYAN));Button b=button("＋ RÉSERVER MA PARTIE");b.setOnClickListener(v->{section="Réservations";render();});reserve.addView(b);content.addView(reserve);
        LinearLayout host=new LinearLayout(this);host.setOrientation(LinearLayout.VERTICAL);content.addView(host);loadUnifiedOpenSessions(host,60,true);
    }

    private void loadUnifiedOpenSessions(LinearLayout host,int limit,boolean detailed){
        loadJson("/api/open-sessions", publicContent->{
                host.removeAllViews();
                JSONArray sessions=publicContent.optJSONArray("sessions");if(sessions==null){host.addView(cardText("Journées indisponibles","Actualise dans quelques instants."));return;}
                LinearLayout head=card();head.setBackground(makeBg(CARD_2,18,NeonPalette.GREEN));
                head.addView(tv("JOURNÉES OUVERTES",11,true,NeonPalette.GREEN));
                head.addView(tv("Le terrain est ouvert ? Les joueurs doivent le voir.",21,true,TEXT));
                head.addView(tv("Parties ouvertes + événements publics sont regroupés ici. Une réservation privée reste invisible.",12,false,MUTED));
                if(!detailed){Button all=secondaryButton("VOIR TOUTES LES JOURNÉES");all.setOnClickListener(v->{section="Parties ouvertes";render();});head.addView(all);}host.addView(head);
                if(sessions.length()==0){host.addView(cardText("Aucune journée ouverte","Aucune session publique n’est annoncée pour le moment. Les réservations privées ne sont jamais affichées ici."));return;}
                int shown=0;for(int i=0;i<sessions.length()&&shown<limit;i++){JSONObject session=sessions.optJSONObject(i);if(session==null)continue;host.addView(buildOpenGameCard(session,detailed));shown++;}
        });
    }

    private JSONArray mergeOpenSessions(JSONObject platform,JSONObject publicContent){
        JSONArray out=new JSONArray();HashSet<String> keys=new HashSet<>();
        JSONArray games=platform==null?null:platform.optJSONArray("games");
        if(games!=null)for(int i=0;i<games.length();i++){
            JSONObject src=games.optJSONObject(i);if(src==null)continue;
            try{JSONObject x=new JSONObject(src.toString());x.put("_source","open_game");String key=openSessionKey(x.optString("gameDate"),x.optString("startTime"),x.optString("title"));keys.add(key);out.put(x);}catch(Exception ignored){}
        }
        JSONArray events=publicContent==null?null:publicContent.optJSONArray("events");
        if(events!=null)for(int i=0;i<events.length();i++){
            JSONObject e=events.optJSONObject(i);if(e==null)continue;String date=e.optString("eventDate","");String time=e.optString("startTime","");String title=e.optString("title","Événement Unité");
            String key=openSessionKey(date,time,title);if(keys.contains(key))continue;
            try{JSONObject x=new JSONObject();x.put("id","event:"+e.optString("id",String.valueOf(i)));x.put("title",title);x.put("gameDate",date);x.put("startTime",time);x.put("capacity",Math.max(0,e.optInt("capacity",0)));x.put("participants",-1);x.put("description",e.optString("description",""));x.put("price",e.optDouble("price",0));x.put("_source","event");out.put(x);keys.add(key);}catch(Exception ignored){}
        }
        ArrayList<JSONObject> rows=new ArrayList<>();for(int i=0;i<out.length();i++){JSONObject x=out.optJSONObject(i);if(x!=null)rows.add(x);}rows.sort((a,b)->(a.optString("gameDate","")+" "+a.optString("startTime","")).compareTo(b.optString("gameDate","")+" "+b.optString("startTime","")));
        JSONArray sorted=new JSONArray();for(JSONObject x:rows)sorted.put(x);return sorted;
    }

    private String openSessionKey(String date,String time,String title){return (date+"|"+time+"|"+title).toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();}

    private LinearLayout buildOpenGameCard(JSONObject game,boolean detailed){
        LinearLayout c=card();c.setBackground(makeBg(CARD_2,18,NeonPalette.GREEN));
        String source=game.optString("_source","open_game");boolean event="event".equals(source);boolean bookingAuto="booking_auto".equals(source);
        int participants=game.optInt("participants",event?-1:0),capacity=Math.max(0,game.optInt("capacity",event?0:16));int remaining=participants>=0&&capacity>0?Math.max(0,capacity-participants):-1;
        c.addView(tv(game.optString("title",event?"Événement public":"Partie ouverte"),20,true,TEXT));
        c.addView(tv(game.optString("gameDate","—")+" · "+game.optString("startTime","—"),14,true,NeonPalette.CYAN_BRIGHT));
        c.addView(tv(event?"ÉVÉNEMENT PUBLIC · terrain ouvert":bookingAuto?"PARTIE OUVERTE · terrain non privatisé":"PARTIE OUVERTE · joueurs acceptés",11,true,event?NeonPalette.CYAN:NeonPalette.GREEN));
        if(event){String desc=game.optString("description","");if(!desc.isEmpty())c.addView(tv(desc,13,false,MUTED));if(capacity>0)c.addView(infoRow("Capacité annoncée",String.valueOf(capacity)));double price=game.optDouble("price",0);if(price>0)c.addView(infoRow("Tarif indicatif",String.format(Locale.FRANCE,"%.2f €",price)));}
        else{
            LinearLayout stats=row();stats.addView(stat("Participants",participants+" / "+capacity),weight());stats.addView(stat("Places",String.valueOf(Math.max(0,remaining))),weight());c.addView(stats);
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(Math.max(1,capacity));bar.setProgress(Math.min(Math.max(0,participants),Math.max(1,capacity)));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(NeonPalette.GREEN));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(8));bp.setMargins(0,dp(5),0,dp(8));c.addView(bar,bp);
        }
        if(detailed&&!event){String level=game.optString("level","");String scenario=game.optString("scenario","");String atmosphere=game.optString("atmosphere","");if(!level.isEmpty())c.addView(infoRow("Niveau",level));if(!scenario.isEmpty())c.addView(infoRow("Scénario",scenario));if(!atmosphere.isEmpty())c.addView(infoRow("Ambiance",atmosphere));}
        if(event||bookingAuto){Button book=button("＋ RÉSERVER SUR CETTE JOURNÉE");book.setOnClickListener(v->openEventBooking(game));c.addView(book);}
        else{Button join=remaining>0?button("＋ S’AJOUTER À CETTE PARTIE"):secondaryButton("PARTIE COMPLÈTE");join.setEnabled(remaining>0);String gameId=game.optString("id","");join.setOnClickListener(v->requestJoinOpenGame(gameId,join));c.addView(join);}
        return c;
    }

    private void openEventBooking(JSONObject event){
        pendingOpenSessionDate=event.optString("gameDate","");pendingOpenSessionTime=event.optString("startTime","");pendingOpenSessionTitle=event.optString("title","Journée ouverte");section="Nouvelle réservation";render();
    }

    private void requestJoinOpenGame(String gameId,Button button){
        if(gameId==null||gameId.isEmpty()){Toast.makeText(this,"Partie introuvable.",Toast.LENGTH_LONG).show();return;}button.setEnabled(false);JSONObject body=new JSONObject();try{body.put("action","join");body.put("id",gameId);}catch(Exception ignored){}
        io.execute(()->{try{JSONObject result=SyncApi.post(this,"/api/open-sessions",store.token(),body);runOnUiThread(()->{button.setText("DEMANDE ENVOYÉE ✓");Toast.makeText(this,result.optBoolean("_queued",false)?"Demande conservée hors ligne · envoi automatique à la reconnexion":"Demande envoyée. L’administrateur pourra la confirmer.",Toast.LENGTH_LONG).show();});}catch(Exception e){runOnUiThread(()->{button.setEnabled(true);Toast.makeText(this,"Impossible de rejoindre : "+safeError(e),Toast.LENGTH_LONG).show();});}});
    }

    private void renderNewsFeed(){
        content.addView(sectionIntro("COMMUNAUTÉ","Fil d’actualité","Photos, vidéos, événements et informations Unité Paintball."));
        LinearLayout tools=card();tools.setBackground(makeBg(CARD_2,18,NeonPalette.CYAN));LinearLayout actions=row();Button photo=button("＋ PHOTO");photo.setOnClickListener(v->openGalleryPicker(false));Button gallery=secondaryButton("GALERIE COMPLÈTE");gallery.setOnClickListener(v->{section="Galerie";render();});actions.addView(photo,new LinearLayout.LayoutParams(0,dp(50),1));LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(0,dp(50),1);gp.setMargins(dp(8),0,0,0);actions.addView(gallery,gp);tools.addView(actions);content.addView(tools);
        loadJson("/api/content", data->{JSONArray events=data.optJSONArray("events"),galleryArr=data.optJSONArray("gallery");if(events!=null&&events.length()>0){LinearLayout ec=card();ec.addView(tv("À VENIR",11,true,NeonPalette.GREEN));for(int i=0;i<Math.min(3,events.length());i++){JSONObject e=events.optJSONObject(i);if(e==null)continue;TextView line=tv(e.optString("title","Événement")+" · "+e.optString("eventDate",e.optString("date","")),15,true,TEXT);line.setPadding(0,dp(7),0,dp(7));ec.addView(line);}ec.setOnClickListener(v->{section="Événements";render();});content.addView(ec);}if(galleryArr==null||galleryArr.length()==0){content.addView(cardText("Aucune publication","Les prochaines photos et vidéos apparaîtront ici."));return;}for(int i=0;i<Math.min(40,galleryArr.length());i++){JSONObject g=galleryArr.optJSONObject(i);if(g!=null)content.addView(buildHomeSocialPost(g,i==0));}});
    }

    private void renderAdminValidationsHub(){
        content.addView(sectionIntro("ADMIN","À valider","Les actions qui demandent ton attention, regroupées sans fouiller dans les menus."));
        LinearLayout c=card();c.setBackground(makeBg(CARD_2,18,ORANGE));c.addView(tv("CENTRE DE VALIDATION",11,true,ORANGE));c.addView(tv("Commence ici",22,true,TEXT));c.addView(tv("Comptes, réservations, modifications, adhésions et participations.",12,false,MUTED));Button all=button("VOIR TOUT CE QUI ATTEND");all.setOnClickListener(v->loadAdminTasks());c.addView(all);content.addView(c);
        LinearLayout r1=row();r1.addView(adminUxTile("Nouveaux joueurs","Comptes à accepter",this::loadAdminMembers),weight());r1.addView(adminUxTile("Réservations","Demandes à traiter",this::loadAdminBookings),weight());content.addView(r1);
        LinearLayout r2=row();r2.addView(adminUxTile("Participations","Parties ouvertes",this::loadAdminParticipations),weight());r2.addView(adminUxTile("Modifications","Avant / après",this::loadAdminBookingChanges),weight());content.addView(r2);
        LinearLayout r3=row();r3.addView(adminUxTile("Confirmations","Présence à confirmer",this::loadAdminConfirmations),weight());r3.addView(adminUxTile("Adhésions","Demandes & statuts",()->{section="Adhésions";render();}),weight());content.addView(r3);
        LinearLayout r4=row();r4.addView(adminUxTile("Messages","Joueurs & équipe",()->{section="Messages";render();}),weight());r4.addView(adminUxTile("Notifications","Alertes administrateur",()->{section="Notifications";render();}),weight());content.addView(r4);
    }

    private void renderAdminTerrainHub(){
        content.addView(sectionIntro("ADMIN","Terrain","Les outils opérationnels du jour, accessibles en un geste."));
        LinearLayout c=card();c.setBackground(makeBg(CARD_2,18,NeonPalette.CYAN));c.addView(tv("OPÉRATIONS TERRAIN",11,true,NeonPalette.CYAN));c.addView(tv("Aujourd'hui",24,true,TEXT));Button scan=button("⌁ SCANNER UN QR");scan.setOnClickListener(v->startActivity(new Intent(this,ScannerActivity.class)));c.addView(scan);Button live=secondaryButton("● OUVRIR LA PARTIE LIVE");live.setOnClickListener(v->loadAdminLive());c.addView(live);content.addView(c);
        LinearLayout r1=row();r1.addView(adminUxTile("Stocks","Billes & matériel",()->{section="Stocks";render();}),weight());r1.addView(adminUxTile("Incidents","Sécurité terrain",()->{section="Incidents";render();}),weight());content.addView(r1);
        LinearLayout r2=row();r2.addView(adminUxTile("Tournois","Gestion compétition",()->{section="Tournois";render();}),weight());r2.addView(adminUxTile("Messages","Équipe & joueurs",()->{section="Messages";render();}),weight());content.addView(r2);
        LinearLayout r3=row();r3.addView(adminUxTile("Live Game","Partie & terrain en direct",this::loadAdminLive),weight());content.addView(r3);
    }

    private void renderAdminManagementHub(){
        content.addView(sectionIntro("ADMIN","Gestion complète","Tous les outils restent disponibles, mais regroupés proprement par métier."));
        Button gifts=button("Bons cadeaux · Créer et scanner");gifts.setOnClickListener(v->startActivity(new Intent(this,GiftCertificateActivity.class)));content.addView(gifts);
        Button complete=secondaryButton("Tous les outils administrateur");complete.setOnClickListener(v->openSharedFeature("/fonctionnalites"));content.addView(complete);
        LinearLayout communication=card();communication.addView(tv("COMMUNICATION",11,true,NeonPalette.CYAN));
        LinearLayout c1=row();c1.addView(adminUxTile("Messages","Conversations joueurs",()->{section="Messages";render();}),weight());c1.addView(adminUxTile("Notifications","Push & alertes",()->{section="Notifications";render();}),weight());communication.addView(c1);content.addView(communication);

        LinearLayout planning=card();planning.addView(tv("RÉSERVATIONS & JOUEURS",11,true,NeonPalette.GREEN));
        LinearLayout p1=row();p1.addView(adminUxTile("Réservations","Planning complet",this::loadAdminBookings),weight());p1.addView(adminUxTile("Confirmations","Présence & rappels",this::loadAdminConfirmations),weight());planning.addView(p1);
        LinearLayout p2=row();p2.addView(adminUxTile("Participations","Parties ouvertes",this::loadAdminParticipations),weight());p2.addView(adminUxTile("Adhésions","Demandes annuelles",()->{section="Adhésions";render();}),weight());planning.addView(p2);content.addView(planning);

        LinearLayout operations=card();operations.addView(tv("GESTION DU TERRAIN",11,true,NeonPalette.CYAN));
        LinearLayout o1=row();o1.addView(adminUxTile("Stocks","Billes & matériel",()->{section="Stocks";render();}),weight());o1.addView(adminUxTile("Incidents","Sécurité & secours",()->{section="Incidents";render();}),weight());operations.addView(o1);
        LinearLayout o2=row();o2.addView(adminUxTile("Galerie","Médias & actualités",()->{section="Galerie";render();}),weight());o2.addView(adminUxTile("Tournois","Compétitions",()->{section="Tournois";render();}),weight());operations.addView(o2);content.addView(operations);

        LinearLayout business=card();business.addView(tv("PILOTAGE",11,true,NeonPalette.GREEN));
        LinearLayout b1=row();b1.addView(adminUxTile("Comptabilité","Recettes, dépenses, exports",()->{section="Finance admin";render();}),weight());b1.addView(adminUxTile("Statistiques","Fréquentation & résultats",()->loadAdminGeneric("Statistiques","/api/admin-dashboard","Statistiques et indicateurs du site.")),weight());business.addView(b1);
        LinearLayout b2=row();b2.addView(adminUxTile("Gamification","XP, missions, roulette",()->loadAdminGeneric("Gamification","/api/admin-gamification","Configuration et suivi Gamification.")),weight());b2.addView(adminUxTile("Arena","Grades, équipements, classement",()->loadAdminGeneric("Arena","/api/arena","Suivi Arena synchronisé avec le site.")),weight());business.addView(b2);content.addView(business);

        LinearLayout system=card();system.addView(tv("SYSTÈME",11,true,NeonPalette.CYAN));
        LinearLayout y1=row();y1.addView(adminUxTile("Studio partagé","Site + application",()->startActivity(new Intent(this,StudioActivity.class))),weight());y1.addView(adminUxTile("Paramètres","Règles métier",this::loadAdminSettingsNative),weight());system.addView(y1);
        LinearLayout y2=row();y2.addView(adminUxTile("Sauvegardes","Protection D1 + médias",this::loadAdminBackups),weight());y2.addView(adminUxTile("Tous les outils","Menu admin complet",this::showMainMenu),weight());system.addView(y2);content.addView(system);
    }

    private LinearLayout adminUxTile(String titleText,String detail,Runnable action){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(15),dp(14),dp(15),dp(14));c.setBackground(makeBg(CARD_2,16,STROKE));c.addView(tv(titleText,17,true,TEXT));c.addView(tv(detail,12,false,MUTED));TextView go=tv("OUVRIR  →",10,true,ORANGE);go.setPadding(0,dp(9),0,0);c.addView(go);c.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(4),dp(5),dp(4),dp(5));c.setLayoutParams(lp);return c;
    }

    private void renderHomeLegacy(){
        JSONObject p=snapshot.optJSONObject("profile");
        String initials=p==null?"UP":initials(join(p.optString("firstName"),p.optString("lastName")));

        // En-tête social inspiré Facebook/X, mais avec Réservation toujours prioritaire.
        LinearLayout composer=card();
        LinearLayout top=row();
        TextView av=tv(initials,16,true,LIME);av.setGravity(Gravity.CENTER);av.setBackground(makeBg(CARD_2,999,LIME));
        top.addView(av,new LinearLayout.LayoutParams(dp(50),dp(50)));
        TextView prompt=tv("Quoi de neuf à Unité Paintball ?",15,false,Color.rgb(206,210,207));prompt.setGravity(Gravity.CENTER_VERTICAL);prompt.setPadding(dp(16),0,dp(10),0);prompt.setBackground(makeBg(CARD_2,999,STROKE));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,dp(50),1);pp.setMargins(dp(10),0,0,0);top.addView(prompt,pp);composer.addView(top);
        LinearLayout publish=row();
        Button photo=miniSocialButton("▧ Photo"); photo.setOnClickListener(v->openGalleryPicker(false));
        Button video=miniSocialButton("▶ Vidéo"); video.setOnClickListener(v->openGalleryPicker(true));
        Button event=miniSocialButton("◉ Événement"); event.setOnClickListener(v->{section="Événements";render();});
        Button message=miniSocialButton("✉ Message"); message.setOnClickListener(v->{section="Messages";render();});
        publish.addView(photo,new LinearLayout.LayoutParams(0,dp(44),1));publish.addView(video,new LinearLayout.LayoutParams(0,dp(44),1));publish.addView(event,new LinearLayout.LayoutParams(0,dp(44),1));publish.addView(message,new LinearLayout.LayoutParams(0,dp(44),1));
        composer.addView(publish);content.addView(composer);

        // Carte Réservation conservée et mise en avant.
        JSONObject next=snapshot.optJSONObject("next");
        LinearLayout booking=new LinearLayout(this);booking.setOrientation(LinearLayout.VERTICAL);booking.setPadding(dp(18),dp(18),dp(18),dp(18));
        GradientDrawable bookingBg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(218,255,63),NeonPalette.CYAN});bookingBg.setCornerRadius(dp(20));booking.setBackground(bookingBg);
        booking.addView(tv("RÉSERVATION",12,true,NeonPalette.PRIMARY_TEXT));
        booking.addView(tv(next==null?"Réserve ta prochaine partie":"Ta prochaine partie",24,true,NeonPalette.PRIMARY_TEXT));
        if(next!=null){
            String when=(next.optString("date")+" · "+next.optString("time")).trim();
            if(!when.equals("·"))booking.addView(tv(when,15,true,Color.rgb(30,38,20)));
            String st=next.optString("status","");if(!st.isEmpty())booking.addView(tv(st+" · "+next.optInt("players",0)+" joueur(s)",13,false,Color.rgb(50,50,30)));
        }else booking.addView(tv("Choisis une date, une formule et ton nombre de joueurs.",13,false,Color.rgb(50,50,30)));
        Button reserve=new Button(this);reserve.setAllCaps(false);reserve.setText(next==null?"Réserver maintenant →":"Gérer ma réservation →");reserve.setTextColor(PRIMARY_TEXT);reserve.setTypeface(null,Typeface.BOLD);reserve.setBackground(makeBg(LIME,12,LIME));reserve.setOnClickListener(v->{section=next==null?"Réservations":"Espace joueur";render();});
        LinearLayout.LayoutParams rbp=new LinearLayout.LayoutParams(-1,dp(48));rbp.setMargins(0,dp(14),0,0);booking.addView(reserve,rbp);content.addView(booking);

        // Stories / accès rapides.
        HorizontalScrollView stories=new HorizontalScrollView(this);stories.setHorizontalScrollBarEnabled(false);
        LinearLayout storyRow=new LinearLayout(this);storyRow.setOrientation(LinearLayout.HORIZONTAL);storyRow.setPadding(0,dp(12),0,dp(12));
        String[][] ss={{"＋","Publier","Galerie"},{"QR","Mon QR","QR"},{"●","Live","Live"},{"♙","Joueurs","Joueurs"},{"♛","Tournois","Tournois"},{"★","Badges","Points"}};
        for(String[] x:ss){LinearLayout sc=socialStory(x[0],x[1]);sc.setOnClickListener(v->{section=x[2];render();});storyRow.addView(sc);}
        stories.addView(storyRow);content.addView(stories);

        LinearLayout feedTabs=row();
        TextView actualites=tabLabel("Actualités",true);TextView events=tabLabel("Événements",false);TextView mediaTab=tabLabel("Photos / vidéos",false);
        actualites.setOnClickListener(v->{});
        events.setOnClickListener(v->{section="Événements";render();});
        mediaTab.setOnClickListener(v->{section="Galerie";galleryMode="Pour toi";render();});
        feedTabs.addView(actualites,new LinearLayout.LayoutParams(0,dp(48),1));feedTabs.addView(events,new LinearLayout.LayoutParams(0,dp(48),1));feedTabs.addView(mediaTab,new LinearLayout.LayoutParams(0,dp(48),1));content.addView(feedTabs);

        loadJson("/api/content", data -> {
            JSONArray gallery=data.optJSONArray("gallery");
            JSONArray eventsArr=data.optJSONArray("events");
            if((gallery==null||gallery.length()==0)&&(eventsArr==null||eventsArr.length()==0)){content.addView(cardText("Unité Paintball","Les prochaines actualités apparaîtront ici."));return;}
            if(gallery!=null)for(int i=0;i<Math.min(gallery.length(),15);i++){JSONObject g=gallery.optJSONObject(i);if(g!=null)content.addView(buildHomeSocialPost(g,i==0));}
            if(eventsArr!=null&&eventsArr.length()>0){
                LinearLayout eventCard=card();eventCard.addView(tv("ÉVÉNEMENTS À VENIR",12,true,ORANGE));
                for(int i=0;i<Math.min(3,eventsArr.length());i++){JSONObject e=eventsArr.optJSONObject(i);if(e==null)continue;TextView t=tv(e.optString("title",e.optString("name","Événement")),17,true,TEXT);t.setPadding(0,dp(10),0,dp(4));eventCard.addView(t);eventCard.addView(tv(e.optString("date",e.optString("startsAt","")),12,false,MUTED));}
                eventCard.setOnClickListener(v->{section="Événements";render();});content.addView(eventCard);
            }
        });
    }

    private Button miniSocialButton(String text){Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setTextColor(TEXT);b.setTextSize(11);b.setBackground(makeBg(Color.TRANSPARENT,10,0));return b;}

    private LinearLayout socialStory(String icon,String label){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);TextView i=tv(icon,20,true,LIME);i.setGravity(Gravity.CENTER);i.setBackground(makeBg(CARD_2,999,LIME));c.addView(i,new LinearLayout.LayoutParams(dp(58),dp(58)));TextView l=tv(label,11,true,TEXT);l.setGravity(Gravity.CENTER);l.setPadding(0,dp(5),0,0);c.addView(l);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(78),dp(92));cp.setMargins(0,0,dp(7),0);c.setLayoutParams(cp);return c;}

    private LinearLayout buildSocialPost(JSONObject g,boolean compact,boolean autoPlayVideo){
        return buildHomeSocialPost(g,autoPlayVideo);
    }

    private LinearLayout buildHomeSocialPost(JSONObject g,boolean autoPlayVideo){
        LinearLayout post=card();
        LinearLayout ph=row();
        ImageView avatar=new ImageView(this);avatar.setImageResource(R.drawable.unite_paintball_logo);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);avatar.setBackground(makeBg(Color.BLACK,999,0));ph.addView(avatar,new LinearLayout.LayoutParams(dp(52),dp(52)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,0,0);
        String author=g.optString("authorName",g.optString("author","Unité Paintball"));copy.addView(tv(author.isEmpty()?"Unité Paintball":author,17,true,TEXT));
        String meta=g.optString("createdAt","");String album=g.optString("album","");copy.addView(tv((album.isEmpty()?"Publication":album)+(meta.isEmpty()?"":" · "+formatSocialDate(meta)),12,false,MUTED));ph.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        TextView more=tv("•••",20,true,MUTED);more.setGravity(Gravity.CENTER);ph.addView(more,new LinearLayout.LayoutParams(dp(44),dp(44)));post.addView(ph);

        String titleText=g.optString("title","").trim();String caption=g.optString("caption",g.optString("description","")).trim();
        if(!titleText.isEmpty()){TextView t=tv(titleText,20,true,TEXT);t.setPadding(0,dp(14),0,0);post.addView(t);}
        if(!caption.isEmpty()){TextView t=tv(caption,15,false,TEXT);t.setLineSpacing(0,1.15f);t.setPadding(0,dp(8),0,dp(10));post.addView(t);}
        String media=g.optString("mediaUrl",g.optString("url",""));boolean video=isVideoMedia(g,media);
        if(!media.isEmpty()){if(video)loadGalleryVideo(post,media,autoPlayVideo);else loadGalleryImage(post,media);}

        int likes=g.optInt("likes",g.optInt("likeCount",0)),comments=g.optInt("comments",g.optInt("commentCount",0));
        LinearLayout counts=row();TextView lc=tv((likes>0?"♥ "+likes+" J’aime":"Soyez le premier à aimer"),13,false,MUTED);counts.addView(lc,new LinearLayout.LayoutParams(0,-2,1));TextView cc=tv(comments+" commentaire"+(comments==1?"":"s"),13,false,MUTED);cc.setGravity(Gravity.RIGHT);counts.addView(cc,new LinearLayout.LayoutParams(0,-2,1));counts.setPadding(0,dp(10),0,dp(8));post.addView(counts);

        View divider=new View(this);divider.setBackgroundColor(STROKE);post.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        LinearLayout actions=row();String id=mediaIdentity(g,media);
        boolean liked=g.optBoolean("liked",false);Button like=feedActionButton(liked?"♥":"♡",liked&&likes>0?likes+" J’aime":"J’aime");if(liked)like.setTextColor(ORANGE);like.setOnClickListener(v->optimisticGalleryLike(like,lc,id,liked,likes));
        Button comment=feedActionButton("💬","Commenter");comment.setOnClickListener(v->focusHomeComment(post));
        Button share=feedActionButton("↗","Partager");share.setOnClickListener(v->shareMedia(media,titleText));
        Button fav=feedActionButton(isFavorite(id)?"★":"☆","Favori");fav.setOnClickListener(v->{toggleFavorite(id);render();});
        actions.addView(like,new LinearLayout.LayoutParams(0,dp(60),1));actions.addView(comment,new LinearLayout.LayoutParams(0,dp(60),1));actions.addView(share,new LinearLayout.LayoutParams(0,dp(60),1));actions.addView(fav,new LinearLayout.LayoutParams(0,dp(60),1));post.addView(actions);
        View divider2=new View(this);divider2.setBackgroundColor(STROKE);post.addView(divider2,new LinearLayout.LayoutParams(-1,dp(1)));

        addRecentComments(post,g);
        addHomeCommentComposer(post,id);
        return post;
    }

    private Button feedActionButton(String icon,String label){
        Button b=new Button(this);b.setAllCaps(false);b.setText(icon+"  "+label);b.setTextColor(TEXT);b.setTextSize(14);b.setTypeface(null,Typeface.BOLD);b.setGravity(Gravity.CENTER);b.setPadding(dp(4),0,dp(4),0);b.setBackground(makeBg(Color.TRANSPARENT,10,0));return b;
    }

    private void addHomeCommentComposer(LinearLayout post,String id){
        LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(0,dp(10),0,dp(2));
        TextView avatar=tv("UP",11,true,LIME);avatar.setGravity(Gravity.CENTER);avatar.setBackground(makeBg(CARD_2,999,LIME));line.addView(avatar,new LinearLayout.LayoutParams(dp(38),dp(38)));
        EditText input=editField("Écrire un commentaire…","");input.setSingleLine(false);input.setMaxLines(3);input.setTextSize(13);input.setTag("home_comment_input");LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,dp(46),1);ip.setMargins(dp(8),0,dp(8),0);line.addView(input,ip);
        Button send=iconButton("➤");send.setTextColor(BG);send.setBackground(makeBg(LIME,999,LIME));send.setOnClickListener(v->{String text=input.getText().toString().trim();if(text.isEmpty())return;sendGallerySocial("comment",id,text);input.setText("");});line.addView(send,new LinearLayout.LayoutParams(dp(46),dp(46)));post.addView(line);
    }

    private void focusHomeComment(LinearLayout post){
        View v=findTaggedView(post,"home_comment_input");if(v instanceof EditText){v.requestFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(v,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);}
    }

    private View findTaggedView(View root,String tag){
        if(tag.equals(root.getTag()))return root;if(root instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)root;for(int i=0;i<g.getChildCount();i++){View x=findTaggedView(g.getChildAt(i),tag);if(x!=null)return x;}}return null;
    }

    private void addRecentComments(LinearLayout post,JSONObject data){
        JSONArray arr=null;Object a=data.opt("recentComments");if(a instanceof JSONArray)arr=(JSONArray)a;else{a=data.opt("commentItems");if(a instanceof JSONArray)arr=(JSONArray)a;else{a=data.opt("commentsList");if(a instanceof JSONArray)arr=(JSONArray)a;else{a=data.opt("comments");if(a instanceof JSONArray)arr=(JSONArray)a;}}}
        if(arr==null||arr.length()==0)return;
        TextView label=tv("Commentaires récents",12,true,MUTED);label.setPadding(0,dp(9),0,dp(3));post.addView(label);
        for(int i=0;i<Math.min(arr.length(),3);i++){JSONObject c=arr.optJSONObject(i);if(c==null)continue;String who=c.optString("authorName",c.optString("author",c.optString("name","Joueur")));String text=c.optString("text",c.optString("comment",c.optString("message","")));if(text.isEmpty())continue;TextView line=tv(who+"  "+text,13,false,TEXT);line.setPadding(dp(8),dp(4),dp(8),dp(4));post.addView(line);}
    }

    private String formatSocialDate(String raw){
        if(raw==null)return "";return raw.replace("T"," ").replace(".000Z","").replace("Z","");
    }

    private LinearLayout buildImmersiveGalleryPost(JSONObject g,boolean autoPlayVideo){
        LinearLayout post=new LinearLayout(this);post.setOrientation(LinearLayout.VERTICAL);post.setPadding(0,0,0,dp(16));post.setBackground(makeBg(CARD,20,STROKE));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.setMargins(dp(2),dp(8),dp(2),dp(12));post.setLayoutParams(pp);
        String media=g.optString("mediaUrl",g.optString("url",""));String id=mediaIdentity(g,media);String title=g.optString("title","");boolean video=isVideoMedia(g,media);
        FrameLayout stage=new FrameLayout(this);stage.setBackgroundColor(Color.BLACK);int h=Math.max(dp(390),(int)(getResources().getDisplayMetrics().heightPixels*.60f));post.addView(stage,new LinearLayout.LayoutParams(-1,h));
        if(media.isEmpty()){TextView none=tv("Média indisponible",15,true,MUTED);none.setGravity(Gravity.CENTER);stage.addView(none,new FrameLayout.LayoutParams(-1,-1));}
        else if(video)loadImmersiveVideo(stage,media,autoPlayVideo);else loadImmersiveImage(stage,media);

        LinearLayout caption=new LinearLayout(this);caption.setOrientation(LinearLayout.VERTICAL);caption.setPadding(dp(16),dp(13),dp(16),dp(8));String author=g.optString("authorName",g.optString("author","Unité Paintball"));caption.addView(tv(author.isEmpty()?"Unité Paintball":author,15,true,TEXT));if(!title.isEmpty()){TextView t=tv(title,18,true,TEXT);t.setPadding(0,dp(5),0,0);caption.addView(t);}String cap=g.optString("caption",g.optString("description","")).trim();if(!cap.isEmpty()){TextView t=tv(cap,13,false,MUTED);t.setPadding(0,dp(4),0,0);caption.addView(t);}post.addView(caption);

        int likes=g.optInt("likes",g.optInt("likeCount",0)),comments=g.optInt("comments",g.optInt("commentCount",0));
        LinearLayout actions=row();actions.setPadding(dp(8),dp(4),dp(8),0);
        boolean liked=g.optBoolean("liked",false);Button like=feedActionButton(liked?"♥":"♡",likes>0?String.valueOf(likes):"J’aime");if(liked)like.setTextColor(ORANGE);like.setOnClickListener(v->optimisticGalleryLike(like,null,id,liked,likes));
        Button comment=feedActionButton("💬",comments>0?String.valueOf(comments):"Com.");comment.setOnClickListener(v->askGalleryComment(id));
        Button share=feedActionButton("↗","Partager");share.setOnClickListener(v->shareMedia(media,title));
        Button fav=feedActionButton(isFavorite(id)?"★":"☆","Favori");fav.setOnClickListener(v->{toggleFavorite(id);render();});
        actions.addView(like,new LinearLayout.LayoutParams(0,dp(54),1));actions.addView(comment,new LinearLayout.LayoutParams(0,dp(54),1));actions.addView(share,new LinearLayout.LayoutParams(0,dp(54),1));actions.addView(fav,new LinearLayout.LayoutParams(0,dp(54),1));post.addView(actions);
        return post;
    }

    private LinearLayout railAction(String icon,String label,View.OnClickListener click){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setPadding(0,dp(4),0,dp(6));box.setOnClickListener(click);
        TextView i=tv(icon,27,true,TEXT);i.setGravity(Gravity.CENTER);i.setBackground(makeBg(Color.argb(150,0,0,0),999,Color.argb(90,255,255,255)));box.addView(i,new LinearLayout.LayoutParams(dp(52),dp(52)));
        TextView l=tv(label,11,true,TEXT);l.setGravity(Gravity.CENTER);l.setShadowLayer(3,0,1,Color.BLACK);box.addView(l,new LinearLayout.LayoutParams(dp(70),dp(24)));return box;
    }

    private void loadImmersiveImage(FrameLayout stage,String mediaPath){
        ProgressBar spinner=new ProgressBar(this);stage.addView(spinner,new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.CENTER));
        prefetchIo.execute(()->{try{java.io.File f=diskCache.mediaFile(mediaPath);if(!f.isFile()||f.length()==0)f=diskCache.downloadMedia(mediaPath,store.token());Bitmap bmp=android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());if(bmp!=null)runOnUiThread(()->{stage.removeView(spinner);ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);iv.setBackgroundColor(Color.BLACK);iv.setImageBitmap(bmp);stage.addView(iv,0,new FrameLayout.LayoutParams(-1,-1));});}catch(Exception e){runOnUiThread(()->stage.removeView(spinner));}});
    }

    private void loadImmersiveVideo(FrameLayout stage,String mediaPath,boolean autoPlay){
        ProgressBar spinner=new ProgressBar(this);stage.addView(spinner,new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.CENTER));
        prefetchIo.execute(()->{try{java.io.File f=diskCache.mediaFile(mediaPath);if(!f.isFile()||f.length()==0)f=diskCache.downloadMedia(mediaPath,store.token());final java.io.File ff=f;runOnUiThread(()->{stage.removeView(spinner);VideoView vv=new VideoView(this);vv.setVideoURI(Uri.fromFile(ff));vv.setOnPreparedListener(mp->{mp.setLooping(true);if(autoPlay){mp.setVolume(0f,0f);vv.start();}});vv.setOnClickListener(v->{if(vv.isPlaying())vv.pause();else vv.start();});stage.addView(vv,0,new FrameLayout.LayoutParams(-1,-1));});}catch(Exception e){runOnUiThread(()->stage.removeView(spinner));}});
    }

    private LinearLayout siteQuickCard(String icon,String heading,String sub,boolean bright){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.BOTTOM|Gravity.LEFT);c.setPadding(dp(16),dp(16),dp(16),dp(16));
        if(bright){
            GradientDrawable gd=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{NeonPalette.CYAN,NeonPalette.GREEN});gd.setCornerRadius(dp(18));gd.setStroke(dp(1),NeonPalette.CYAN_BRIGHT);c.setBackground(gd);
        } else c.setBackground(makeBg(CARD_2,18,STROKE));
        c.addView(tv(icon,30,true,bright?Color.rgb(5,20,14):TEXT));
        c.addView(tv(heading,17,true,bright?Color.rgb(5,20,14):TEXT));
        c.addView(tv(sub,12,false,bright?Color.argb(150,5,20,14):MUTED));
        return c;
    }

    private void renderBookings(){
        content.addView(sectionIntro("MES RÉSERVATIONS", "Réservations", "Retrouve tes prochaines parties et gère tes demandes."));
        LinearLayout available=card();available.setBackground(makeBg(CARD_2,18,ORANGE));available.addView(tv("PARTIES DÉJÀ DISPONIBLES",12,true,ORANGE));available.addView(tv("Avant de créer une nouvelle réservation, regarde les journées ouvertes et rejoins une partie qui a déjà des places.",14,false,MUTED));Button seeOpen=button("VOIR LES PARTIES DISPONIBLES");seeOpen.setOnClickListener(v->{section="Parties ouvertes";render();});available.addView(seeOpen);content.addView(available);
        LinearLayout rotationInfo=card(); rotationInfo.setBackground(makeBg(CARD_2,18,LIME)); rotationInfo.addView(tv("20 joueurs à la fois · jusqu’à 40 par réservation",18,true,LIME)); rotationInfo.addView(tv("Le terrain accueille 20 joueurs maximum simultanément. De 21 à 40 participants, le groupe est réparti automatiquement en Groupe A / Groupe B et alterne entre les manches. Les autres réservations de la journée continuent sur leurs propres créneaux.",13,false,MUTED)); content.addView(rotationInfo);
        Button create=button("＋ Nouvelle réservation");
        create.setOnClickListener(v->{section="Nouvelle réservation";render();});
        content.addView(create,new LinearLayout.LayoutParams(-1,dp(52)));
        JSONArray arr=snapshot.optJSONArray("upcoming");
        if(arr==null||arr.length()==0){
            LinearLayout empty=card();
            empty.addView(tv("Aucune réservation à venir",20,true,TEXT));
            TextView sub=tv("Choisis une date, une formule et prépare ta prochaine session au terrain.",13,false,MUTED);sub.setPadding(0,dp(7),0,dp(10));empty.addView(sub);
            Button go=secondaryButton("Voir les événements");go.setOnClickListener(v->{section="Événements";render();});empty.addView(go);
            content.addView(empty);return;
        }
        for(int i=0;i<arr.length();i++){
            JSONObject b=arr.optJSONObject(i);if(b==null)continue;
            LinearLayout c=card();
            LinearLayout head=row();
            LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);
            TextView ref=tv("RÉSERVATION · "+b.optString("reference"),11,true,LIME);ref.setLetterSpacing(.05f);left.addView(ref);
            TextView date=tv(b.optString("date")+" · "+b.optString("time"),21,true,TEXT);date.setPadding(0,dp(5),0,0);left.addView(date);
            head.addView(left,new LinearLayout.LayoutParams(0,-2,1));
            head.addView(statusPill(b.optString("status","En attente")));
            c.addView(head);
            c.addView(infoRow("Joueurs", b.optInt("players",0)+""));
            if(!b.optString("offer").isEmpty()) c.addView(infoRow("Formule",b.optString("offer")));
            if(!b.optString("eventType").isEmpty()) c.addView(infoRow("Occasion",b.optString("eventType")));
            if(snapshot.optBoolean("ownBooking",false) && snapshot.optJSONObject("next")!=null && b.optString("id").equals(snapshot.optJSONObject("next").optString("id"))){
                LinearLayout actions=row();
                Button modify=secondaryButton("Modifier");modify.setOnClickListener(v->renderBookingChange(b));
                Button qr=secondaryButton("Mon QR");qr.setOnClickListener(v->{section="QR";render();});
                actions.addView(modify,new LinearLayout.LayoutParams(0,dp(46),1));
                LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(0,dp(46),1);qp.setMargins(dp(8),dp(10),0,0);actions.addView(qr,qp);
                c.addView(actions);
            }
            content.addView(c);
        }
    }

        private String normalizeBookingDate(String value){
        String v=value==null?"":value.trim();
        try{if(v.matches("\\d{4}-\\d{2}-\\d{2}"))return v;if(v.matches("\\d{2}/\\d{2}/\\d{4}")){String[] p=v.split("/");return p[2]+"-"+p[1]+"-"+p[0];}}catch(Exception ignored){}
        return "";
    }

        private boolean isMinorBirthDate(String value){
        try{return Period.between(LocalDate.parse(value),LocalDate.now()).getYears()<18;}catch(Exception e){return false;}
    }

    private void syncParticipantForms(LinearLayout holder,ArrayList<ParticipantForm> forms,int players){
        int wanted=Math.max(0,players-1);
        while(forms.size()<wanted){
            final int index=forms.size();ParticipantForm f=new ParticipantForm();
            f.card=new LinearLayout(this);f.card.setOrientation(LinearLayout.VERTICAL);f.card.setPadding(dp(14),dp(14),dp(14),dp(14));f.card.setBackground(makeBg(CARD_2,16,STROKE));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(10));f.card.setLayoutParams(cp);
            f.title=tv("Joueur "+(index+2),16,true,TEXT);f.card.addView(f.title);
            f.firstName=editField("Prénom du joueur "+(index+2)+" *","");f.lastName=editField("Nom du joueur "+(index+2)+" *","");f.birthDate=editField("Date de naissance (AAAA-MM-JJ) *","");f.birthDate.setHint("2008-04-17");
            f.card.addView(f.firstName);f.card.addView(f.lastName);f.card.addView(f.birthDate);
            f.guardianSection=new LinearLayout(this);f.guardianSection.setOrientation(LinearLayout.VERTICAL);f.guardianSection.setPadding(dp(12),dp(10),dp(12),dp(10));f.guardianSection.setBackground(makeBg(AppUiPrefs.isLight(this)?Color.rgb(255,246,226):CARD_2,14,ORANGE));
            f.guardianSection.addView(tv("Joueur mineur · autorisation parentale obligatoire",13,true,ORANGE));
            f.guardianName=editField("Nom et prénom du responsable légal *","");f.guardianPhone=editField("Téléphone du responsable légal *","");f.guardianPhone.setInputType(InputType.TYPE_CLASS_PHONE);f.parentalAuthorization=acceptBox("Le responsable légal confirme autoriser l'inscription de ce mineur et s'engage à compléter et signer la décharge parentale avant la partie. *");
            f.guardianSection.addView(f.guardianName);f.guardianSection.addView(f.guardianPhone);f.guardianSection.addView(f.parentalAuthorization);f.card.addView(f.guardianSection);f.guardianSection.setVisibility(View.GONE);
            final ParticipantForm ff=f;f.birthDate.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence x,int st,int c,int a){}public void onTextChanged(CharSequence x,int st,int before,int count){boolean minor=isMinorBirthDate(x.toString().trim());ff.guardianSection.setVisibility(minor?View.VISIBLE:View.GONE);ff.title.setText("Joueur "+(index+2)+(minor?" · MINEUR":""));}public void afterTextChanged(Editable e){}});
            forms.add(f);holder.addView(f.card);
        }
        while(forms.size()>wanted){ParticipantForm f=forms.remove(forms.size()-1);holder.removeView(f.card);}
    }

    private JSONArray participantPayload(ArrayList<ParticipantForm> forms)throws Exception{
        JSONArray out=new JSONArray();
        for(int i=0;i<forms.size();i++){ParticipantForm f=forms.get(i);String fn=f.firstName.getText().toString().trim(),ln=f.lastName.getText().toString().trim(),bd=f.birthDate.getText().toString().trim();if(fn.isEmpty()||ln.isEmpty()||bd.isEmpty())throw new Exception("Complète le prénom, le nom et la date de naissance du joueur "+(i+2)+".");boolean minor=isMinorBirthDate(bd);String gn=f.guardianName.getText().toString().trim(),gp=f.guardianPhone.getText().toString().trim();if(minor&&(gn.isEmpty()||gp.replaceAll("\\D","").length()<8||!f.parentalAuthorization.isChecked()))throw new Exception("Le joueur "+(i+2)+" est mineur : complète le responsable légal et confirme l'autorisation parentale.");out.put(new JSONObject().put("firstName",fn).put("lastName",ln).put("birthDate",bd).put("guardianName",minor?gn:"").put("guardianPhone",minor?gp:""));}
        return out;
    }

    private EditText bookingGiftInput;
    private boolean bookingGiftScan=false;
    private void renderNewBooking(){
        final JSONObject[] bookingGift={null};
        final Runnable[] repriceGift={null};
        final int[] giftGeneration={0};
        LinearLayout giftCard=card();giftCard.addView(tv("J’ai un bon cadeau",20,true,TEXT));
        bookingGiftInput=editField("Code ou lien du bon cadeau","");final EditText giftInput=bookingGiftInput;giftCard.addView(giftInput);
        TextView giftStatus=tv("Scanne le QR ou colle son code. Le scan ne consomme pas le bon.",13,false,MUTED);giftCard.addView(giftStatus);
        Button giftCheck=secondaryButton("Vérifier et appliquer le bon");giftCheck.setOnClickListener(v->{String code=GiftCertificatePolicy.code(giftInput.getText().toString(),AppConfig.BASE_URL);if(code.isEmpty()){giftStatus.setText("Code ou lien de bon cadeau invalide.");return;}int ticket=++giftGeneration[0];giftCheck.setEnabled(false);io.execute(()->{try{JSONObject g=ApiClient.get("/api/bons-cadeaux?code="+android.net.Uri.encode(code),store.token());if(!"active".equals(g.optString("state")))throw new IllegalArgumentException("Bon déjà réservé, utilisé, annulé ou expiré.");runOnUiThread(()->{giftCheck.setEnabled(true);if(ticket!=giftGeneration[0])return;bookingGift[0]=g;giftStatus.setText(g.optString("title")+" · "+g.optString("benefit")+"\n"+g.optString("conditions"));if(repriceGift[0]!=null)repriceGift[0].run();});}catch(Exception e){runOnUiThread(()->{giftCheck.setEnabled(true);if(ticket==giftGeneration[0])giftStatus.setText(e.getMessage());});}});});giftCard.addView(giftCheck);
        Button giftScan=secondaryButton("Scanner le QR du bon");giftScan.setOnClickListener(v->{bookingGiftScan=true;com.google.zxing.integration.android.IntentIntegrator scan=new com.google.zxing.integration.android.IntentIntegrator(this);scan.setDesiredBarcodeFormats(com.google.zxing.integration.android.IntentIntegrator.QR_CODE);scan.setPrompt("Scanner le bon cadeau");scan.initiateScan();});giftCard.addView(giftScan);
        giftInput.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence t,int start,int count,int after){}public void onTextChanged(CharSequence t,int start,int before,int count){giftGeneration[0]++;bookingGift[0]=null;giftStatus.setText("Vérifie ce bon avant de l’appliquer.");if(repriceGift[0]!=null)repriceGift[0].run();}public void afterTextChanged(Editable t){}});content.addView(giftCard);
        JSONObject p=snapshot.optJSONObject("profile"), u=snapshot.optJSONObject("user");
        if(p==null)p=new JSONObject(); if(u==null)u=new JSONObject();

        TextView step=tv("1. CHOISIS TA FORMULE",14,true,MUTED); step.setLetterSpacing(.16f); step.setPadding(0,dp(18),0,dp(12)); content.addView(step);
        RadioGroup offers=new RadioGroup(this); offers.setOrientation(RadioGroup.VERTICAL);
        final String[] offerCodes={"entry","base","group","private"};
        final String[] offerNames={"Accès terrain","Formule Découverte","Formule Groupe","Terrain privatisé"};
        final String[] offerSubs={"Sans billes · Pour joueurs équipés","1 000 billes · Location disponible","2 000 billes · À partir de 10 joueurs","Sans billes · Billes en supplément · Tarif fixe"};
        final int[] base={20,35,50,200};
        final boolean[] sessionMode={false,false,false,true};
        final boolean[] equipmentIncluded={false,false,false,false};
        final int[] offerMin={1,1,10,1},offerMax={40,40,40,40};
        JSONObject cachedCommercial=diskCache==null?null:diskCache.getJson("/api/commercial");
        if(cachedCommercial!=null){JSONArray rows=cachedCommercial.optJSONArray("offers");if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);if(x==null)continue;String code=x.optString("code","");for(int j=0;j<offerCodes.length;j++)if(offerCodes[j].equals(code)){offerNames[j]=x.optString("name",offerNames[j]);offerSubs[j]=x.optString("description",offerSubs[j]);base[j]=x.optInt("price",base[j]);sessionMode[j]="session".equals(x.optString("pricingMode",sessionMode[j]?"session":"person"));equipmentIncluded[j]=x.optBoolean("equipmentIncluded",equipmentIncluded[j]);offerMin[j]=Math.max(1,x.optInt("minimumPlayers",offerMin[j]));offerMax[j]=Math.max(1,x.optInt("maximumPlayers",offerMax[j]));}}}
        final int[] equipmentRate={10},exclusiveDayRate={100},privateSlotRate={20},hourlyRate={20},morningRate={0},afternoonRate={0},eveningRate={0},freeDurationHours={1};
        final HashSet<String> freeSlotOfferCodes=new HashSet<>();
        final int[] ballCounts={0,500,1000,2000,4000,6000};
        final int[] ballPrices={0,15,30,60,110,160};
        final String[] ballLabels={"Aucune bille supplémentaire","500 billes","1 000 billes","2 000 billes","4 000 billes","6 000 billes"};
        JSONObject cachedPricing=diskCache==null?null:diskCache.getJson("/api/player-pricing");
        if(cachedPricing!=null){equipmentRate[0]=cachedPricing.optInt("equipment",equipmentRate[0]);exclusiveDayRate[0]=cachedPricing.optInt("exclusiveDay",exclusiveDayRate[0]);privateSlotRate[0]=cachedPricing.optInt("privateSlotRate",privateSlotRate[0]);hourlyRate[0]=cachedPricing.optInt("hourlyRate",hourlyRate[0]);morningRate[0]=cachedPricing.optInt("morningRate",morningRate[0]);afternoonRate[0]=cachedPricing.optInt("afternoonRate",afternoonRate[0]);eveningRate[0]=cachedPricing.optInt("eveningRate",eveningRate[0]);freeDurationHours[0]=Math.max(0,cachedPricing.optInt("freeDurationHours",freeDurationHours[0]));JSONArray freeCodes=cachedPricing.optJSONArray("freeSlotOfferCodes");if(freeCodes!=null)for(int i=0;i<freeCodes.length();i++){String code=freeCodes.optString(i,"");if(!code.isEmpty())freeSlotOfferCodes.add(code);}JSONArray packs=cachedPricing.optJSONArray("ballPacks");if(packs!=null)for(int i=0;i<Math.min(packs.length(),ballCounts.length);i++){JSONObject b=packs.optJSONObject(i);if(b!=null){ballCounts[i]=b.optInt("count",ballCounts[i]);ballPrices[i]=b.optInt("price",ballPrices[i]);ballLabels[i]=b.optString("label",ballLabels[i]);}}}
        String[] offerPrices=new String[4];for(int i=0;i<4;i++)offerPrices[i]=base[i]+(sessionMode[i]?" € / session":" € / personne");
        for(int i=0;i<offerNames.length;i++){
            RadioButton rb=new RadioButton(this); rb.setId(6000+i); rb.setText(offerNames[i]+"\n"+offerSubs[i]+"\n"+offerPrices[i]); rb.setTextColor(TEXT); rb.setTextSize(15); rb.setGravity(Gravity.CENTER_VERTICAL); rb.setPadding(dp(14),dp(12),dp(14),dp(12)); rb.setButtonTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{NeonPalette.CYAN,Color.rgb(115,95,119)}));
            rb.setBackground(makeBg(i==1?Color.rgb(12,48,58):Color.rgb(8,31,39),18,i==1?NeonPalette.CYAN:Color.rgb(32,83,94)));
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(i==3?122:110)); rp.setMargins(0,0,0,dp(10)); offers.addView(rb,rp);
        }
        offers.check(6001); content.addView(offers);

        TextView playersLabel=tv("Nombre de joueurs",14,true,MUTED); playersLabel.setPadding(0,dp(10),0,dp(8)); content.addView(playersLabel);
        LinearLayout counter=row(); counter.setGravity(Gravity.CENTER_VERTICAL); counter.setPadding(dp(8),dp(8),dp(8),dp(8)); counter.setBackground(makeBg(CARD_2,16,STROKE));
        Button minus=secondaryButton("−"); TextView playerValue=tv("1 / 40",18,true,TEXT); playerValue.setGravity(Gravity.CENTER); Button plus=button("+");
        counter.addView(minus,new LinearLayout.LayoutParams(dp(62),dp(54))); counter.addView(playerValue,new LinearLayout.LayoutParams(0,dp(54),1)); counter.addView(plus,new LinearLayout.LayoutParams(dp(62),dp(54))); content.addView(counter);
        TextView rotation=tv("20 joueurs maximum sur le terrain à la fois. De 21 à 40 participants : rotations automatiques Groupe A / Groupe B entre les manches.",11,false,MUTED); rotation.setPadding(0,dp(8),0,dp(10)); content.addView(rotation);
        final int[] playerCount={1};
        final int[] bookingMax={40},bookingMin={1},simultaneousCap={20},confirmationLead={24},freeCancelLead={48};
        ArrayList<ParticipantForm> participantForms=new ArrayList<>();
        LinearLayout participantBox=new LinearLayout(this);participantBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout participantIntro=card();participantIntro.setBackground(makeBg(CARD_2,16,LIME));participantIntro.addView(tv("Participants du groupe",18,true,TEXT));participantIntro.addView(tv("Pour chaque joueur ajouté, renseigne prénom, nom et date de naissance. Si la personne a moins de 18 ans, les coordonnées du responsable légal et l'autorisation parentale deviennent obligatoires.",12,false,MUTED));participantBox.addView(participantIntro);participantBox.setVisibility(View.GONE);content.addView(participantBox);

        EditText date=editField("Date souhaitée",""); date.setHint("jj/mm/aaaa");
        Spinner time=spinner(new String[]{"09:00","10:00","11:00","12:00","13:00","14:00","15:00","16:00","17:00","18:00"}); time.setSelection(5);
        if(!pendingOpenSessionDate.isEmpty()){date.setText(pendingOpenSessionDate);String wanted=pendingOpenSessionTime;for(int i=0;i<time.getCount();i++)if(String.valueOf(time.getItemAtPosition(i)).equals(wanted)){time.setSelection(i);break;}if(!pendingOpenSessionTitle.isEmpty()){Toast.makeText(this,"Journée sélectionnée : "+pendingOpenSessionTitle,Toast.LENGTH_SHORT).show();}pendingOpenSessionDate="";pendingOpenSessionTime="";pendingOpenSessionTitle="";}
        Spinner duration=spinner(new String[]{"1 heure · jusqu’à 15 h","2 heures","3 heures","4 heures"});
        content.addView(date); content.addView(tv("Heure de début",13,true,MUTED)); content.addView(time); content.addView(tv("Durée de jeu",13,true,MUTED)); content.addView(duration);
        LinearLayout availability=card(); availability.setBackground(makeBg(CARD_2,16,ORANGE)); availability.addView(tv("Disponibilité du créneau",16,true,TEXT)); TextView availabilityText=tv("Choisis une date pour vérifier les places réelles.",12,false,MUTED); availabilityText.setPadding(0,dp(6),0,0); availability.addView(availabilityText); content.addView(availability);
        final TextView[] policyTexts={null,null};
        final Runnable updateAvailability=()->{
            String d=normalizeBookingDate(date.getText().toString());
            if(d.isEmpty()){availabilityText.setText("Choisis une date au format JJ/MM/AAAA ou AAAA-MM-JJ.");return;}
            final int requestPlayers=playerCount[0];
            int selectedStart=9;
            try{selectedStart=Integer.parseInt(String.valueOf(time.getSelectedItem()).substring(0,2));}catch(Exception ignored){}
            final int requestStart=selectedStart,requestDuration=duration.getSelectedItemPosition()+1;
            io.execute(()->{
                try{
                    JSONObject a=ApiClient.get("/api/availability?date="+d,store.token());
                    int cap=Math.max(1,a.optInt("capacity",20));
                    int groupMax=Math.max(1,a.optInt("sessionCapacity",Math.min(40,cap*2)));
                    int minimum=Math.max(1,a.optInt("minimumToPlay",1));
                    int openStart=a.optInt("openingStart",9),openEnd=a.optInt("openingEnd",19);
                    int confirm=Math.max(1,a.optInt("confirmationHours",24));
                    int freeCancel=Math.max(0,a.optInt("freeCancellationHours",48));
                    int needed=requestPlayers<=cap?requestPlayers:(requestPlayers+1)/2;
                    JSONObject occ=a.optJSONObject("occupancy");JSONArray blocked=a.optJSONArray("blockedHours");
                    boolean blockedSlot=false;int remaining=cap;
                    for(int h=requestStart;h<requestStart+requestDuration;h++){
                        String key=String.format(Locale.US,"%02d:00",h);
                        int used=occ==null?0:occ.optInt(key,0);remaining=Math.min(remaining,cap-used);
                        if(blocked!=null)for(int i=0;i<blocked.length();i++)if(key.equals(blocked.optString(i)))blockedSlot=true;
                    }
                    final boolean bs=blockedSlot,allowed=a.optBoolean("allowed",false),exclusive=a.optBoolean("exclusive",false),outsideHours=requestStart<openStart||requestStart+requestDuration>openEnd;
                    final int rem=remaining,need=needed,c=cap,gmax=Math.min(40,groupMax),min=minimum,os=openStart,oe=openEnd,ch=confirm,fh=freeCancel;
                    runOnUiThread(()->{
                        simultaneousCap[0]=c;bookingMax[0]=gmax;bookingMin[0]=min;confirmationLead[0]=ch;freeCancelLead[0]=fh;
                        rotation.setText(c+" joueurs maximum sur le terrain à la fois. Au-delà : rotations automatiques Groupe A / Groupe B. Maximum actuel : "+gmax+" participants.");
                        int selectedOffer=offers.getCheckedRadioButtonId()-6000;if(selectedOffer<0||selectedOffer>=offerMax.length)selectedOffer=1;
                        int effectiveMax=Math.min(gmax,offerMax[selectedOffer]);int effectiveMin=Math.max(min,offerMin[selectedOffer]);
                        playerValue.setText(playerCount[0]+" / "+effectiveMax);
                        if(policyTexts[0]!=null)policyTexts[0].setText("Conditions d’annulation : annulation gratuite jusqu’à "+fh+" h avant la session. Après ce délai, les conditions acceptées s’appliquent. Confirmation demandée "+ch+" h avant la partie.");
                        if(policyTexts[1]!=null)policyTexts[1].setText("Confirmation obligatoire "+ch+" h avant la partie : je confirme que mon numéro WhatsApp est correct et je m’engage à répondre au lien envoyé. Sans confirmation à cette échéance, ma réservation peut être annulée et les places proposées aux réservistes. *");
                        String desiredFirst=String.format(Locale.US,"%02d:00",os),desiredLast=String.format(Locale.US,"%02d:00",Math.max(os,oe-1));
                        boolean timesChanged=time.getAdapter()==null||time.getAdapter().getCount()!=Math.max(1,oe-os)||!desiredFirst.equals(String.valueOf(time.getAdapter().getItem(0)))||!desiredLast.equals(String.valueOf(time.getAdapter().getItem(Math.max(0,time.getAdapter().getCount()-1))));
                        if(timesChanged){
                            String current=String.valueOf(time.getSelectedItem());String[] hours=new String[Math.max(1,oe-os)];
                            for(int i=0;i<hours.length;i++)hours[i]=String.format(Locale.US,"%02d:00",os+i);
                            ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,hours);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);time.setAdapter(adapter);
                            int idx=0;for(int i=0;i<hours.length;i++)if(hours[i].equals(current))idx=i;time.setSelection(idx);
                        }
                        int currentPlayers=playerCount[0];int currentNeed=currentPlayers<=c?currentPlayers:(currentPlayers+1)/2;
                        if(!allowed)availabilityText.setText("Date fermée aux réservations.");
                        else if(outsideHours)availabilityText.setText("La durée choisie dépasse les horaires d’ouverture de cette date.");
                        else if(currentPlayers<effectiveMin)availabilityText.setText("Il faut au moins "+effectiveMin+" joueur(s) pour cette formule et ce créneau.");
                        else if(currentPlayers>effectiveMax)availabilityText.setText("Le maximum actuel pour cette formule est de "+effectiveMax+" participants.");
                        else if(exclusive)availabilityText.setText("Journée privatisée : aucun autre groupe ne peut réserver.");
                        else if(bs)availabilityText.setText("Créneau privatisé : choisis un autre horaire.");
                        else if(rem<currentNeed)availabilityText.setText("Il faut "+currentNeed+" place(s) simultanée(s), mais seulement "+Math.max(0,rem)+" restent disponibles.");
                        else availabilityText.setText(rem+" place(s) simultanée(s) restante(s) sur "+c+" · ton groupe en occupera "+currentNeed+" à la fois.");
                    });
                }catch(Exception e){runOnUiThread(()->availabilityText.setText("Disponibilité indisponible pour le moment · le serveur revalidera avant confirmation."));}
            });
        };

        CheckBox exclusiveSlot=acceptBox("Privatiser le créneau choisi · +"+privateSlotRate[0]+" € par heure");
        CheckBox exclusiveDay=acceptBox("Bloquer toute la journée pour votre groupe · +"+exclusiveDayRate[0]+" €");
        CheckBox rent=acceptBox("Location masque + lanceur · "+equipmentRate[0]+" € / joueur"); rent.setChecked(!"personnel".equals(p.optString("equipmentMode")));
        content.addView(exclusiveSlot); content.addView(exclusiveDay); content.addView(rent);
        content.addView(tv("Billes supplémentaires",14,true,MUTED));
        String[] ballChoices=new String[ballLabels.length];for(int i=0;i<ballLabels.length;i++)ballChoices[i]=ballLabels[i]+(ballPrices[i]>0?" — "+ballPrices[i]+" €":""); Spinner balls=spinner(ballChoices); content.addView(balls);
        content.addView(tv("Venez-vous pour une occasion particulière ?",14,true,MUTED));
        Spinner eventType=spinner(new String[]{"Aucun événement particulier","Réservation classique","EVG / EVJF","Anniversaire","Mariage","Groupe / entreprise / association"}); content.addView(eventType);
        content.addView(tv("Modes / scénarios de jeu (facultatif)",14,true,MUTED));
        Spinner scenarios=spinner(new String[]{"Choisir les scénarios","Classique","Capture du drapeau","Infection Zombies","VIP","Défense de zone"}); content.addView(scenarios);

        LinearLayout estimate=new LinearLayout(this); estimate.setOrientation(LinearLayout.VERTICAL); estimate.setPadding(dp(18),dp(18),dp(18),dp(18)); estimate.setBackground(makeBg(NeonPalette.CYAN,22,NeonPalette.CYAN)); LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2); ep.setMargins(0,dp(14),0,dp(14)); estimate.setLayoutParams(ep);
        TextView estOver=tv("ESTIMATION DÉTAILLÉE",11,true,Color.rgb(113,55,0)); estOver.setLetterSpacing(.15f); estimate.addView(estOver);
        TextView estTitle=tv("Formule Découverte",22,true,Color.rgb(30,25,21)); estTitle.setPadding(0,dp(10),0,dp(4)); estimate.addView(estTitle);
        TextView estSub=tv("1 joueur × 35 €",13,false,Color.rgb(120,68,20)); estimate.addView(estSub);
        TextView estTotal=tv("45 €",30,true,Color.rgb(20,20,20)); estTotal.setGravity(Gravity.RIGHT); estimate.addView(estTotal);
        TextView estDetails=tv("Formule 35 €\nLocation (1 joueur) 10 €\n\n🛡 Paiement uniquement sur place",13,false,Color.rgb(45,31,20)); estDetails.setPadding(0,dp(8),0,0); estimate.addView(estDetails); content.addView(estimate);

        final Runnable updateEstimate=()->{
            int selected=offers.getCheckedRadioButtonId()-6000; if(selected<0||selected>3)selected=1;
            int formula=sessionMode[selected]?base[selected]:base[selected]*playerCount[0]; int rental=rent.isChecked()&&!equipmentIncluded[selected]?equipmentRate[0]*playerCount[0]:0; int ballExtra=ballPrices[balls.getSelectedItemPosition()]; int durHours=duration.getSelectedItemPosition()+1; int startHour=9;try{startHour=Integer.parseInt(String.valueOf(time.getSelectedItem()).substring(0,2));}catch(Exception ignored){}String selectedCode=offerCodes[selected];boolean includedSlot=freeSlotOfferCodes.contains(selectedCode);int extraHours=Math.max(0,durHours-freeDurationHours[0]);int periodRate=startHour<12?morningRate[0]:(startHour<18?afternoonRate[0]:eveningRate[0]);int slotDuration=(includedSlot||exclusiveSlot.isChecked())?0:(extraHours*hourlyRate[0]+(durHours>0?periodRate:0));int privatePart=exclusiveSlot.isChecked()&&!includedSlot?privateSlotRate[0]*durHours:0;int slot=slotDuration+privatePart; int day=exclusiveDay.isChecked()?exclusiveDayRate[0]:0; int total=formula+rental+ballExtra+slot+day;
            estTitle.setText(offerNames[selected]); estSub.setText(sessionMode[selected]?"Tarif fixe":playerCount[0]+" joueur"+(playerCount[0]>1?"s":"")+" × "+base[selected]+" €"); estTotal.setText(total+" €");
            String d="Formule "+formula+" €"+(rental>0?"\nLocation ("+playerCount[0]+" joueur"+(playerCount[0]>1?"s":"")+") "+rental+" €":"")+(ballExtra>0?"\nBilles supplémentaires "+ballExtra+" €":"")+(slot>0?"\nCréneau / durée "+slot+" €":"")+(day>0?"\nJournée bloquée "+day+" €":"")+"\n\n🛡 Paiement uniquement sur place"; estDetails.setText(d);
            if(bookingGift[0]!=null){try{JSONObject effect=bookingGift[0].getJSONObject("effect");int discount=GiftBookingPricing.discount(effect.optString("kind"),effect.optInt("value"),effect.optBoolean("equipment"),effect.optString("offerCode"),offerCodes[selected],playerCount[0],!sessionMode[selected],base[selected],rental>0?playerCount[0]:0,equipmentRate[0],total);estTotal.setText((total-discount)+" €");estDetails.setText(d+"\nBon cadeau : −"+discount+" € (revalidé par le serveur)");}catch(Exception e){estDetails.setText(d+"\nBon non applicable : "+e.getMessage());}}

        };
        minus.setOnClickListener(v->{if(playerCount[0]>1){playerCount[0]--;int selected=offers.getCheckedRadioButtonId()-6000;if(selected<0||selected>=offerMax.length)selected=1;int effectiveMax=Math.min(bookingMax[0],offerMax[selected]);playerValue.setText(playerCount[0]+" / "+effectiveMax);syncParticipantForms(participantBox,participantForms,playerCount[0]);participantBox.setVisibility(playerCount[0]>1?View.VISIBLE:View.GONE);updateEstimate.run();updateAvailability.run();}});
        plus.setOnClickListener(v->{int selected=offers.getCheckedRadioButtonId()-6000;if(selected<0||selected>=offerMax.length)selected=1;int effectiveMax=Math.min(bookingMax[0],offerMax[selected]);if(playerCount[0]<effectiveMax){playerCount[0]++;playerValue.setText(playerCount[0]+" / "+effectiveMax);syncParticipantForms(participantBox,participantForms,playerCount[0]);participantBox.setVisibility(View.VISIBLE);updateEstimate.run();updateAvailability.run();}});
        repriceGift[0]=updateEstimate;
        offers.setOnCheckedChangeListener((g,id)->{int selected=id-6000;if(selected>=0&&selected<4){int max=Math.min(bookingMax[0],offerMax[selected]);if(playerCount[0]>max)playerCount[0]=max;if(playerCount[0]<offerMin[selected])playerCount[0]=offerMin[selected];playerValue.setText(playerCount[0]+" / "+max);syncParticipantForms(participantBox,participantForms,playerCount[0]);participantBox.setVisibility(playerCount[0]>1?View.VISIBLE:View.GONE);}updateEstimate.run();updateAvailability.run();}); rent.setOnCheckedChangeListener((b,c)->updateEstimate.run()); exclusiveSlot.setOnCheckedChangeListener((b,c)->updateEstimate.run()); exclusiveDay.setOnCheckedChangeListener((b,c)->updateEstimate.run());
        balls.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){updateEstimate.run();}public void onNothingSelected(android.widget.AdapterView<?> parent){}});
        duration.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){updateEstimate.run();updateAvailability.run();}public void onNothingSelected(android.widget.AdapterView<?> parent){}});
        repriceGift[0]=updateEstimate;
        updateEstimate.run();

        time.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){updateEstimate.run();updateAvailability.run();}public void onNothingSelected(android.widget.AdapterView<?> parent){}});
        date.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence x,int st,int c,int a){}public void onTextChanged(CharSequence x,int st,int before,int count){}public void afterTextChanged(Editable e){updateAvailability.run();}});

        LinearLayout assistant=card(); assistant.setBackground(makeBg(Color.rgb(8,31,39),18,Color.rgb(24,78,88))); TextView ak=tv("ASSISTANT UNITÉ",11,true,NeonPalette.CYAN); ak.setLetterSpacing(.15f); assistant.addView(ak); assistant.addView(tv("Une question ?",23,true,TEXT)); TextView ab=tv("Bonjour ! Je peux t’aider à choisir une formule, connaître les règles ou préparer une réservation.",13,false,Color.rgb(185,170,188));ab.setPadding(0,dp(8),0,dp(8));assistant.addView(ab); EditText aq=editField("Écris ta question…","");assistant.addView(aq);content.addView(assistant);

        content.addView(sectionIntro("2. TES COORDONNÉES", "Tes informations", "Les informations déjà connues de ton compte sont préremplies."));
        EditText firstName=editField("Prénom *",p.optString("firstName"));
        EditText lastName=editField("Nom *",p.optString("lastName"));
        EditText email=editField("E-mail *",u.optString("email")); email.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText phone=editField("Téléphone *",p.optString("phone")); phone.setInputType(InputType.TYPE_CLASS_PHONE);
        EditText birth=editField("Date de naissance *",p.optString("birthDate"));
        Spinner preferredContact=spinner(new String[]{"WhatsApp","Téléphone","E-mail"});
        EditText address=editField("Adresse *",p.optString("address"));
        EditText postal=editField("Code postal *",p.optString("postalCode"));
        EditText city=editField("Ville *",p.optString("city"));
        EditText notes=editField("Message facultatif"," "); notes.setText("");
        EditText emergencyName=editField("Personne à prévenir *","");
        EditText emergencyPhone=editField("Téléphone d'urgence *",""); emergencyPhone.setInputType(InputType.TYPE_CLASS_PHONE);
        EditText guardian=editField("Responsable légal si mineur",p.optString("guardianName"));
        View[] fields={firstName,lastName,email,phone,birth,preferredContact,address,postal,city,notes,emergencyName,emergencyPhone,guardian};
        for(View v:fields)content.addView(v);

        LinearLayout charterCard=card();
        charterCard.setBackground(makeBg(CARD_2,18,ORANGE));
        charterCard.addView(tv("Charte de participation et de sécurité",22,true,TEXT));
        TextView charterVersion=tv("13 septembre 2026 — version 1",13,false,Color.rgb(155,144,158)); charterVersion.setPadding(0,dp(6),0,dp(12)); charterCard.addView(charterVersion);
        Button openCharter=secondaryButton("Ouvrir en grand"); openCharter.setOnClickListener(v->{section="Sécurité";render();}); charterCard.addView(openCharter);
        content.addView(charterCard);

        CheckBox rules=acceptBox("J’ai lu et j’accepte le règlement et toutes les consignes de sécurité. *");
        CheckBox risks=acceptBox("Je reconnais avoir été informé des risques inhérents à la pratique du paintball. *");
        CheckBox medical=acceptBox("Je certifie être apte à participer et ne pas avoir de contre-indication médicale connue. *");
        CheckBox image=acceptBox("J’autorise gratuitement la publication de photos ou vidéos où je peux apparaître. Facultatif.");
        CheckBox charter=acceptBox("Je reconnais avoir lu intégralement la charte du 13 septembre 2026 — version 1, avoir compris ses règles et les accepter sans réserve, sous réserve des droits auxquels la loi interdit de renoncer. *");
        content.addView(rules); content.addView(risks); content.addView(medical); content.addView(image); content.addView(charter);

        LinearLayout sport=card(); sport.setBackground(makeBg(Color.rgb(48,43,51),18,Color.rgb(73,97,116)));
        sport.addView(tv("Garantie complémentaire I.A. Sport+ — choix obligatoire *",19,true,Color.rgb(225,232,239)));
        TextView sportInfo=tv("Dans le cadre du dossier d’assurance sportive de l’association, indiquez si vous souhaitez souscrire cette garantie complémentaire. Aucun choix n’est présélectionné et aucune somme n’est ajoutée automatiquement.",14,false,Color.rgb(192,184,193)); sportInfo.setPadding(0,dp(12),0,dp(12)); sportInfo.setLineSpacing(0,1.18f); sport.addView(sportInfo);
        RadioGroup insurance=new RadioGroup(this); insurance.setOrientation(RadioGroup.VERTICAL);
        RadioButton insuranceYes=new RadioButton(this); insuranceYes.setId(7101); insuranceYes.setText("Je souhaite souscrire la garantie I.A. Sport+"); insuranceYes.setTextColor(TEXT); insuranceYes.setTextSize(14); insuranceYes.setPadding(dp(12),dp(12),dp(12),dp(12)); insuranceYes.setBackground(makeBg(Color.rgb(63,52,62),16,Color.rgb(92,76,92)));
        RadioButton insuranceNo=new RadioButton(this); insuranceNo.setId(7102); insuranceNo.setText("Je ne souhaite pas souscrire cette garantie"); insuranceNo.setTextColor(TEXT); insuranceNo.setTextSize(14); insuranceNo.setPadding(dp(12),dp(12),dp(12),dp(12)); insuranceNo.setBackground(makeBg(Color.rgb(63,52,62),16,Color.rgb(92,76,92)));
        LinearLayout.LayoutParams irp=new LinearLayout.LayoutParams(-1,-2); irp.setMargins(0,0,0,dp(10)); insurance.addView(insuranceYes,irp); insurance.addView(insuranceNo,new LinearLayout.LayoutParams(-1,-2)); sport.addView(insurance);
        TextView insuranceNote=tv("Ce choix est enregistré avec la réservation, la date, l’heure et la version du texte. Les garanties, exclusions et éventuel tarif restent ceux de la notice officielle remise par l’association ou la MAIF.",12,false,Color.rgb(230,220,190)); insuranceNote.setPadding(0,dp(12),0,0); sport.addView(insuranceNote); content.addView(sport);

        LinearLayout cancellation=card(); cancellation.setBackground(makeBg(Color.rgb(60,44,45),18,Color.rgb(112,89,50)));
        TextView cancelText=tv("Conditions d’annulation : annulation gratuite jusqu’à "+freeCancelLead[0]+" h avant la session. Après ce délai, les conditions acceptées s’appliquent. Une confirmation est demandée "+confirmationLead[0]+" h avant la partie.",14,false,Color.rgb(246,235,197)); cancelText.setLineSpacing(0,1.22f); cancellation.addView(cancelText); content.addView(cancellation);policyTexts[0]=cancelText;

        LinearLayout attendanceCard=card(); attendanceCard.setBackground(makeBg(Color.rgb(45,49,52),18,Color.rgb(45,111,81)));
        attendanceCard.addView(tv("Engagement de présence",18,true,Color.rgb(112,235,162)));
        CheckBox attendance=acceptBox("Confirmation obligatoire "+confirmationLead[0]+" h avant la partie : je confirme que mon numéro WhatsApp est correct et je m’engage à répondre au lien envoyé. Sans confirmation à cette échéance, ma réservation peut être annulée et les places proposées aux réservistes. *"); attendanceCard.addView(attendance);policyTexts[1]=attendance;
        TextView idNote=tv("Une pièce d’identité peut être vérifiée sur place, mais elle n’est ni photographiée ni conservée.",12,false,Color.rgb(155,155,158)); attendanceCard.addView(idNote); content.addView(attendanceCard);

        EditText signature=editField("Signature électronique — écrivez votre nom complet *",join(p.optString("firstName"),p.optString("lastName"))); content.addView(signature);
        TextView sigNote=tv("La date et l’heure de signature sont enregistrées avec la demande. Pour un mineur, le représentant légal signe en son nom.",12,false,Color.rgb(151,140,153)); sigNote.setPadding(0,dp(4),0,dp(10)); content.addView(sigNote);
        LinearLayout ageCard=card(); ageCard.setBackground(makeBg(Color.rgb(58,43,45),18,Color.rgb(112,89,50))); ageCard.addView(tv("Contrôle de l’âge : l’ajout d’un justificatif dans le compte est facultatif. Une carte d’identité ou un permis original devra toutefois être présenté obligatoirement à l’accueil avant de jouer.",14,false,Color.rgb(246,235,197))); content.addView(ageCard);

        LinearLayout verify=card(); verify.setBackground(makeBg(Color.rgb(53,33,52),18,Color.rgb(119,105,52)));
        verify.addView(tv("Vérifie ta réservation",22,true,TEXT));
        TextView verifyBody=tv("Date : à compléter\nJoueurs : 1\nFormule : Formule Découverte\nBilles incluses : 500 billes\nSupplément : 0 billes\nRéservant : "+join(p.optString("firstName"),p.optString("lastName"))+"\nParticipants ajoutés : Aucun\nHoraire demandé : 14:00–15:00\nSupplément de créneau : 0 €\nÉvénement : Aucun événement particulier\nScénarios : À choisir sur place\nTotal estimé à régler sur place : 45 €",14,false,Color.rgb(238,231,222)); verifyBody.setPadding(0,dp(12),0,0); verifyBody.setLineSpacing(0,1.24f); verify.addView(verifyBody); content.addView(verify);
        TextView required=tv("Les champs et cases marqués d’un * sont obligatoires. Appuie sur « Envoyer » pour voir précisément ce qui manque.",13,false,Color.rgb(235,220,137)); required.setPadding(0,dp(10),0,dp(12)); content.addView(required);

        Button send=button("Confirmer la réservation avec obligation de paiement"); send.setBackground(makeBg(NeonPalette.CYAN,14,NeonPalette.CYAN)); content.addView(send);
        TextView payNote=tv("Aucune donnée bancaire n’est demandée. Le paiement est réalisé sur place.",11,false,Color.rgb(151,140,153)); payNote.setGravity(Gravity.CENTER); payNote.setPadding(0,dp(8),0,dp(10)); content.addView(payNote);
        send.setOnClickListener(v->{
            try{
                if(firstName.getText().toString().trim().isEmpty()||lastName.getText().toString().trim().isEmpty()||email.getText().toString().trim().isEmpty()||phone.getText().toString().trim().isEmpty()||birth.getText().toString().trim().isEmpty()||address.getText().toString().trim().isEmpty()||postal.getText().toString().trim().isEmpty()||city.getText().toString().trim().isEmpty()||emergencyName.getText().toString().trim().isEmpty()||emergencyPhone.getText().toString().trim().isEmpty()||signature.getText().toString().trim().isEmpty()||normalizeBookingDate(date.getText().toString()).isEmpty()) throw new Exception("Complète tous les champs obligatoires marqués d’un *.");
                if(!rules.isChecked()||!risks.isChecked()||!medical.isChecked()||!attendance.isChecked()||!charter.isChecked()) throw new Exception("Coche toutes les acceptations obligatoires.");
                if(insurance.getCheckedRadioButtonId()==-1) throw new Exception("Choisis si tu souhaites ou non la garantie I.A. Sport+.");
                if(playerCount[0]<bookingMin[0]) throw new Exception("Il faut au moins "+bookingMin[0]+" joueur(s) pour réserver.");
                int selectedOffer=offers.getCheckedRadioButtonId()-6000;if(selectedOffer<0||selectedOffer>3)selectedOffer=1;
                int effectiveMax=Math.min(bookingMax[0],offerMax[selectedOffer]);if(playerCount[0]>effectiveMax)throw new Exception("Le maximum actuel pour cette formule est de "+effectiveMax+" participants.");
                JSONArray participantData=participantPayload(participantForms);if(participantData.length()!=Math.max(0,playerCount[0]-1))throw new Exception("Complète tous les participants du groupe.");
                int selected=offers.getCheckedRadioButtonId()-6000; if(selected<0||selected>3)selected=1;
                JSONArray sc=new JSONArray(); if(scenarios.getSelectedItemPosition()>0)sc.put(String.valueOf(scenarios.getSelectedItem()));
                JSONObject body=new JSONObject().put("firstName",firstName.getText().toString().trim()).put("lastName",lastName.getText().toString().trim()).put("email",email.getText().toString().trim()).put("phone",phone.getText().toString().trim()).put("address",address.getText().toString().trim()).put("postalCode",postal.getText().toString().trim()).put("city",city.getText().toString().trim()).put("birthDate",birth.getText().toString().trim()).put("preferredContact",new String[]{"whatsapp","phone","email"}[preferredContact.getSelectedItemPosition()]).put("date",normalizeBookingDate(date.getText().toString())).put("time",String.valueOf(time.getSelectedItem())).put("durationHours",duration.getSelectedItemPosition()+1).put("offer",offerCodes[selected]).put("players",playerCount[0]).put("eventType",String.valueOf(eventType.getSelectedItem())).put("honoreeRequested",false).put("honoreeIndices",new JSONArray()).put("exclusiveDay",exclusiveDay.isChecked()).put("exclusiveSlot",exclusiveSlot.isChecked()).put("rentEquipment",rent.isChecked()).put("paintballs",ballCounts[balls.getSelectedItemPosition()]).put("notes",notes.getText().toString().trim()).put("emergencyName",emergencyName.getText().toString().trim()).put("emergencyPhone",emergencyPhone.getText().toString().trim()).put("guardianName",guardian.getText().toString().trim()).put("signatureName",signature.getText().toString().trim()).put("rulesAccepted",rules.isChecked()).put("risksAccepted",risks.isChecked()).put("medicalCertified",medical.isChecked()).put("imageConsent",image.isChecked()).put("attendanceAccepted",attendance.isChecked()).put("charterAccepted",charter.isChecked()).put("insuranceChoice",insurance.getCheckedRadioButtonId()==7101?"subscribe":"decline").put("participants",participantData).put("scenarios",sc);
                if(!giftInput.getText().toString().trim().isEmpty()){if(bookingGift[0]==null)throw new IllegalArgumentException("Vérifie et applique le bon avant d’envoyer la réservation.");body.put("giftCode",bookingGift[0].getString("code"));}
                send.setEnabled(false); io.execute(()->{try{JSONObject result=SyncApi.post(this,"/api/bookings",store.token(),body);runOnUiThread(()->{Toast.makeText(this,"Réservation envoyée",Toast.LENGTH_LONG).show();promptMinorDossiers(result);});}catch(Exception e){runOnUiThread(()->{send.setEnabled(true);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});
            }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
        });
        Button cancel=secondaryButton("Retour"); cancel.setOnClickListener(v->{section="Réservations";render();}); content.addView(cancel);
        updateEstimate.run();
    }

    private void renderBookingChange(JSONObject booking){
        content.removeAllViews(); title.setText("Unité Paintball · Modifier réservation");
        content.addView(cardText("Demande de modification",booking.optString("reference")+" · la modification sera validée par l'administration."));
        EditText date=editField("Date",booking.optString("date")); EditText time=editField("Heure",booking.optString("time"));
        EditText players=editField("Nombre de joueurs",String.valueOf(booking.optInt("players",1))); players.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText notes=editField("Notes",booking.optString("notes"));
        content.addView(date);content.addView(time);content.addView(players);content.addView(notes);
        Button send=button("Envoyer la demande");content.addView(send);
        send.setOnClickListener(v->{try{JSONObject body=new JSONObject().put("bookingId",booking.optString("id")).put("date",normalizeBookingDate(date.getText().toString())).put("time",time.getText().toString().trim()).put("players",Integer.parseInt(players.getText().toString().trim())).put("notes",notes.getText().toString().trim());send.setEnabled(false);io.execute(()->{try{SyncApi.post(this,"/api/booking-changes",store.token(),body);runOnUiThread(()->{Toast.makeText(this,"Demande envoyée à l'administration",Toast.LENGTH_LONG).show();section="Réservations";refresh(true);});}catch(Exception e){runOnUiThread(()->{send.setEnabled(true);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});}catch(Exception e){Toast.makeText(this,"Vérifie le nombre de joueurs.",Toast.LENGTH_LONG).show();}});
        Button back=button("Retour");back.setOnClickListener(v->{section="Réservations";render();});content.addView(back);
    }

    private void renderEvents(){
        content.addView(sectionIntro("PROCHAINEMENT", "Événements", "Événements du site et création directe pour les administrateurs autorisés."));
        loadJson("/api/content", data -> {
            JSONArray arr=data.optJSONArray("events");
            if(arr==null||arr.length()==0)content.addView(cardText("Aucun événement ouvert","Les prochaines dates apparaîtront ici automatiquement."));
            else for(int i=0;i<arr.length();i++){
                JSONObject e=arr.optJSONObject(i);if(e==null)continue;
                LinearLayout c=card();LinearLayout top=row();TextView dateBadge=tv(shortDate(e.optString("eventDate")),13,true,BG);dateBadge.setGravity(Gravity.CENTER);dateBadge.setBackground(makeBg(LIME,14,LIME));top.addView(dateBadge,new LinearLayout.LayoutParams(dp(78),dp(52)));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,0,0);copy.addView(tv(e.optString("title","Événement"),19,true,TEXT));copy.addView(tv(e.optString("eventDate")+" · "+e.optString("startTime",""),12,false,MUTED));top.addView(copy,new LinearLayout.LayoutParams(0,-2,1));c.addView(top);String desc=e.optString("description","");if(!desc.isEmpty()){TextView d=tv(desc,13,false,MUTED);d.setPadding(0,dp(12),0,0);c.addView(d);}if(e.optInt("capacity",0)>0)c.addView(infoRow("Capacité",String.valueOf(e.optInt("capacity"))));if(e.optDouble("price",0)>0)c.addView(infoRow("Prix",String.format(Locale.FRANCE,"%.2f €",e.optDouble("price"))));Button b=secondaryButton("Réserver / participer");b.setOnClickListener(v->{section="Réservations";render();});c.addView(b);content.addView(c);
            }
        });
        prefetchIo.execute(()->{try{ApiClient.get("/api/admin-content",store.token());runOnUiThread(()->{if(!"Événements".equals(section))return;Button create=button("＋ Créer un événement");create.setOnClickListener(v->showCreateEventForm());content.addView(create);});}catch(Exception ignored){}});
    }

        private void renderMessages(){
        activeChatChannel="";activeChatLabel="";activeChatSignature="";handler.removeCallbacks(chatSync);
        content.addView(sectionIntro("MESSAGERIE", "Chat", "Conversations privées, groupes et support — actualisation rapide."));
        JSONObject cachedMessages=diskCache.getJson("/api/communication");
        if(cachedMessages!=null){showMessageCenter(cachedMessages);}
        ProgressBar pb=null;if(cachedMessages==null){pb=new ProgressBar(this);content.addView(pb);}final ProgressBar loading=pb;
        io.execute(()->{
            try{JSONObject data=ApiClient.get("/api/communication",store.token());diskCache.putJson("/api/communication",data);final String fresh=data.toString();final String oldCache=cachedMessages==null?"":cachedMessages.toString();runOnUiThread(()->{if(loading!=null&&loading.getParent()!=null)content.removeView(loading);if(cachedMessages==null)showMessageCenter(data);else if(!fresh.equals(oldCache)&&"Messages".equals(section)&&activeChatChannel.isEmpty())renderCurrentSectionFresh();});}
            catch(Exception e){if(cachedMessages==null)runOnUiThread(()->{if(loading!=null&&loading.getParent()!=null)content.removeView(loading);content.addView(cardText("Messagerie hors ligne","Dernière synchronisation indisponible · "+safeError(e)));});}
        });
    }

    private void showMessageCenter(JSONObject data){
        JSONArray channels=data.optJSONArray("channels");JSONObject unread=data.optJSONObject("unread");

        LinearLayout chatHead=row();chatHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView chatTitle=tv("Chat",25,true,TEXT);chatTitle.setGravity(Gravity.CENTER);chatHead.addView(chatTitle,new LinearLayout.LayoutParams(0,dp(54),1));
        Button friends=iconButton("♙+");friends.setTextColor(BG);friends.setBackground(makeBg(NeonPalette.GREEN,999,NeonPalette.GREEN));friends.setContentDescription("Mes amis");friends.setOnClickListener(v->{section="Amis";render();});chatHead.addView(friends,new LinearLayout.LayoutParams(dp(48),dp(48)));content.addView(chatHead);

        EditText search=editField("Rechercher une conversation…","");search.setSingleLine(true);content.addView(search);

        HorizontalScrollView filterScroll=new HorizontalScrollView(this);filterScroll.setHorizontalScrollBarEnabled(false);filterScroll.setClipToPadding(false);
        LinearLayout filters=new LinearLayout(this);filters.setOrientation(LinearLayout.HORIZONTAL);filters.setPadding(0,dp(4),dp(10),dp(10));
        Button unreadBtn=messageFilterButton("Non lus"+(totalUnread(unread)>0?"  "+totalUnread(unread):""),true);
        Button all=messageFilterButton("Tous",false);Button priv=messageFilterButton("Joueurs",false);Button groups=messageFilterButton("Groupes",false);Button support=messageFilterButton("Unité",false);
        filters.addView(unreadBtn);filters.addView(all);filters.addView(priv);filters.addView(groups);filters.addView(support);filterScroll.addView(filters);content.addView(filterScroll);

        if(channels==null||channels.length()==0){content.addView(cardText("Aucune conversation","Tes groupes et messages apparaîtront ici."));return;}
        if((pendingDirectUserId!=null&&!pendingDirectUserId.isEmpty())||(pendingDirectName!=null&&!pendingDirectName.isEmpty())){
            for(int i=0;i<channels.length();i++){JSONObject ch=channels.optJSONObject(i);if(ch==null||!"direct".equals(ch.optString("type","direct")))continue;String peer=ch.optString("userId",ch.optString("peerUserId",ch.optString("recipientUserId","")));String label=cleanChatLabel(ch.optString("label","Conversation"));if((!pendingDirectUserId.isEmpty()&&pendingDirectUserId.equals(peer))||(!pendingDirectName.isEmpty()&&label.toLowerCase(Locale.FRANCE).contains(pendingDirectName.toLowerCase(Locale.FRANCE)))){String cid=ch.optString("id","");String cname=label;pendingDirectUserId="";pendingDirectName="";if(!cid.isEmpty()){openSnapChannel(cid,cname);return;}}}
            Toast.makeText(this,"Conversation privée introuvable pour "+pendingDirectName,Toast.LENGTH_LONG).show();pendingDirectUserId="";pendingDirectName="";
        }
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);content.addView(list);
        final String[] filter={totalUnread(unread)>0?"unread":"all"};
        Runnable redraw=()->populateMessageChannels(list,channels,unread,filter[0],search.getText().toString());
        unreadBtn.setOnClickListener(v->{filter[0]="unread";redraw.run();});all.setOnClickListener(v->{filter[0]="all";redraw.run();});priv.setOnClickListener(v->{filter[0]="players";redraw.run();});groups.setOnClickListener(v->{filter[0]="groups";redraw.run();});support.setOnClickListener(v->{filter[0]="support";redraw.run();});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence cs,int st,int before,int count){redraw.run();}public void afterTextChanged(Editable e){}});redraw.run();
    }

    private Button messageFilterButton(String label,boolean active){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(12);b.setTypeface(null,Typeface.BOLD);b.setTextColor(active?BG:TEXT);b.setBackground(makeBg(active?NeonPalette.GREEN:CARD_2,999,active?NeonPalette.GREEN:STROKE));b.setPadding(dp(16),0,dp(16),0);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(42));lp.setMargins(0,0,dp(8),0);b.setLayoutParams(lp);return b;
    }

    private void populateMessageChannels(LinearLayout list,JSONArray channels,JSONObject unread,String filter,String query){
        list.removeAllViews();String q=query==null?"":query.trim().toLowerCase(Locale.FRANCE);int total=0;
        for(int i=0;i<channels.length();i++){
            JSONObject ch=channels.optJSONObject(i);if(ch==null)continue;String id=ch.optString("id"),name=cleanChatLabel(ch.optString("label","Conversation")),type=ch.optString("type","direct");int unreadCount=unread==null?0:unread.optInt(id,0);
            boolean isDirect="direct".equals(type),isSupport="support".equals(type),isGroup=!isDirect&&!isSupport;boolean ok=q.isEmpty()||name.toLowerCase(Locale.FRANCE).contains(q);
            if("unread".equals(filter))ok=ok&&unreadCount>0;else if("players".equals(filter))ok=ok&&isDirect;else if("groups".equals(filter))ok=ok&&isGroup;else if("support".equals(filter))ok=ok&&isSupport;if(!ok)continue;total++;

            LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.VERTICAL);line.setPadding(0,0,0,0);line.setBackgroundColor(BG);LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(4),dp(12),dp(4),dp(12));
            TextView avatar=tv(initials(name),15,true,unreadCount>0?BG:TEXT);avatar.setGravity(Gravity.CENTER);avatar.setBackground(makeBg(unreadCount>0?NeonPalette.GREEN:CARD_2,999,unreadCount>0?NeonPalette.GREEN:STROKE));r.addView(avatar,new LinearLayout.LayoutParams(dp(58),dp(58)));
            LinearLayout cp=new LinearLayout(this);cp.setOrientation(LinearLayout.VERTICAL);cp.setPadding(dp(13),0,dp(6),0);TextView n=tv(name,18,unreadCount>0,TEXT);cp.addView(n);
            String preview=ch.optString("lastMessage",ch.optString("preview",chatTypeLabel(type)));if(preview==null||preview.trim().isEmpty())preview=chatTypeLabel(type);TextView pv=tv((unreadCount>0?"● ":"")+preview,12,false,unreadCount>0?NeonPalette.CYAN_BRIGHT:MUTED);pv.setSingleLine(true);pv.setEllipsize(android.text.TextUtils.TruncateAt.END);cp.addView(pv);r.addView(cp,new LinearLayout.LayoutParams(0,-2,1));
            if(unreadCount>0){TextView badge=tv(String.valueOf(unreadCount),11,true,BG);badge.setGravity(Gravity.CENTER);badge.setBackground(makeBg(NeonPalette.GREEN,999,NeonPalette.GREEN));r.addView(badge,new LinearLayout.LayoutParams(dp(30),dp(30)));}
            TextView go=tv("›",28,false,MUTED);go.setGravity(Gravity.CENTER);r.addView(go,new LinearLayout.LayoutParams(dp(34),dp(50)));line.addView(r);View divider=new View(this);divider.setBackgroundColor(STROKE);line.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));line.setOnClickListener(v->openSnapChannel(id,name));list.addView(line);
        }
        if(total==0)list.addView(cardText("Aucune conversation","Aucun résultat avec ce filtre."));
        // Marge supplémentaire pour que la dernière conversation ne passe jamais sous la barre système.
        View safe=new View(this);list.addView(safe,new LinearLayout.LayoutParams(1,dp(72)));
    }

    private TextView chatFilter(String label,int count){TextView v=tv(label+(count>0?" "+count:""),12,true,count>0?BG:MUTED);v.setGravity(Gravity.CENTER);v.setBackground(makeBg(count>0?LIME:CARD_2,999,count>0?LIME:STROKE));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(38),1);lp.setMargins(dp(2),0,dp(2),dp(8));v.setLayoutParams(lp);return v;}
    private int totalUnread(JSONObject unread){if(unread==null)return 0;int n=0;java.util.Iterator<String> it=unread.keys();while(it.hasNext())n+=unread.optInt(it.next(),0);return n;}
    private int countUnreadChannels(JSONArray a,JSONObject unread){if(unread==null)return 0;int n=0;for(int i=0;i<a.length();i++){JSONObject c=a.optJSONObject(i);if(c!=null&&unread.optInt(c.optString("id"),0)>0)n++;}return n;}
    private int countType(JSONArray a,String t){int n=0;for(int i=0;i<a.length();i++){JSONObject c=a.optJSONObject(i);if(c!=null&&t.equals(c.optString("type")))n++;}return n;}
    private int countGroups(JSONArray a){return countType(a,"general")+countType(a,"team")+countType(a,"booking");}
    private String chatTypeLabel(String t){if("support".equals(t))return "Support privé";if("direct".equals(t))return "Conversation privée";if("team".equals(t))return "Équipe";if("booking".equals(t))return "Groupe de réservation";return "Communauté";}
    private String cleanChatLabel(String s){return s.replace("Joueur · ","").replace(" · Support admin","");}

    private void openSnapChannel(String channel,String label){
        activeChatChannel=channel;activeChatLabel=label;activeChatSignature="";activeChatDraft="";section="Messages";title.setText(label);handler.removeCallbacks(chatSync);
        io.execute(()->{try{SyncApi.post(this,"/api/communication",store.token(),new JSONObject().put("action","read_channel").put("channel",channel));}catch(Exception ignored){}});
        loadActiveChannel(true);handler.postDelayed(chatSync,CHAT_SYNC_MS);
    }

    private void loadActiveChannel(boolean force){
        final String channel=activeChatChannel,label=activeChatLabel;if(channel.isEmpty())return;
        final String ep="/api/communication?channel="+android.net.Uri.encode(channel);
        JSONObject cached=diskCache.getJson(ep);
        if(force&&cached!=null){JSONArray cm=cached.optJSONArray("messages");String cs=String.valueOf(cm==null?0:cm.length());activeChatSignature=cs;renderChannel(channel,label,cached);}
        io.execute(()->{try{
            JSONObject d=ApiClient.get("/api/communication?channel="+java.net.URLEncoder.encode(channel,"UTF-8"),store.token());diskCache.putJson(ep,d);
            JSONArray msgs=d.optJSONArray("messages");String sig=String.valueOf(msgs==null?0:msgs.length());if(msgs!=null&&msgs.length()>0){JSONObject last=msgs.optJSONObject(msgs.length()-1);if(last!=null)sig+="|"+last.optString("id")+"|"+last.optString("createdAt");}
            final String signature=sig;
            if(force||!signature.equals(activeChatSignature))runOnUiThread(()->{if(channel.equals(activeChatChannel)){if(activeChatInput!=null)activeChatDraft=activeChatInput.getText().toString();activeChatSignature=signature;renderChannel(channel,label,d);}});
        }catch(Exception ignored){}});
    }

    private void renderChannel(String channel,String label,JSONObject d){
        content.removeAllViews();
        LinearLayout head=row();
        Button back=iconButton("‹");back.setTextSize(32);back.setTextColor(TEXT);back.setOnClickListener(v->{activeChatChannel="";handler.removeCallbacks(chatSync);title.setText("Messages");renderMessages();});head.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView avatar=tv(initials(label),13,true,BG);avatar.setGravity(Gravity.CENTER);avatar.setBackground(makeBg(LIME,999,LIME));head.addView(avatar,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout who=new LinearLayout(this);who.setOrientation(LinearLayout.VERTICAL);who.setPadding(dp(10),0,0,0);who.addView(tv(label,18,true,TEXT));who.addView(tv("En ligne · synchronisation automatique",11,false,NeonPalette.GREEN));head.addView(who,new LinearLayout.LayoutParams(0,-2,1));
        TextView more=tv("•••",20,true,MUTED);more.setGravity(Gravity.CENTER);head.addView(more,new LinearLayout.LayoutParams(dp(50),dp(44)));content.addView(head);

        JSONArray msgs=d.optJSONArray("messages");String currentUser=d.optString("currentUserId");
        if(msgs==null||msgs.length()==0){TextView empty=tv("Commencez la conversation 👋",14,false,MUTED);empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(70),0,dp(70));content.addView(empty);}
        else{int start=Math.max(0,msgs.length()-60);for(int i=start;i<msgs.length();i++){
            JSONObject m=msgs.optJSONObject(i);if(m==null)continue;boolean mine=currentUser.equals(m.optString("authorUserId"));
            LinearLayout row=new LinearLayout(this);row.setGravity(mine?Gravity.RIGHT:Gravity.LEFT);row.setPadding(0,dp(3),0,dp(3));
            LinearLayout bubble=new LinearLayout(this);bubble.setOrientation(LinearLayout.VERTICAL);bubble.setPadding(dp(12),dp(9),dp(12),dp(8));bubble.setBackground(makeBg(mine?(AppUiPrefs.isLight(this)?Color.rgb(232,243,225):Color.rgb(43,92,58)):CARD_2,18,mine?LIME:STROKE));
            String body=m.optString("body","");
            if(body.startsWith("[PHOTO] ")||body.startsWith("📷 ")){String media=body.startsWith("[PHOTO] ")?body.substring(8).trim():body.substring(3).trim();TextView photoLabel=tv("Photo",12,true,mine?LIME:ORANGE);bubble.addView(photoLabel);ImageView iv=new ImageView(this);iv.setAdjustViewBounds(true);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);iv.setBackground(makeBg(Color.rgb(20,20,20),12,STROKE));bubble.addView(iv,new LinearLayout.LayoutParams(dp(240),-2));loadChatImage(media,iv);}
            else {bubble.addView(tv(body,15,false,TEXT));}
            LinearLayout meta=row();TextView time=tv(shortTime(m.optString("createdAt")),10,false,MUTED);meta.addView(time,new LinearLayout.LayoutParams(0,-2,1));if(mine)meta.addView(tv("✓ Envoyé",10,false,LIME));bubble.addView(meta);
            row.addView(bubble,new LinearLayout.LayoutParams((int)(getResources().getDisplayMetrics().widthPixels*.76f),-2));content.addView(row);
        }}

        LinearLayout typing=row();typing.setPadding(dp(6),dp(7),dp(6),dp(4));TextView typingText=tv("",11,false,MUTED);typingText.setTag("typing");typing.addView(typingText);content.addView(typing);

        LinearLayout composer=row();composer.setGravity(Gravity.BOTTOM);
        Button photo=iconButton("＋");photo.setTextColor(BG);photo.setTextSize(24);photo.setBackground(makeBg(LIME,999,LIME));photo.setOnClickListener(v->{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.addCategory(Intent.CATEGORY_OPENABLE);pick.setType("image/*");startActivityForResult(pick,REQ_CHAT_IMAGE);});composer.addView(photo,new LinearLayout.LayoutParams(dp(46),dp(46)));
        EditText input=editField("Envoyer un message…",activeChatDraft);input.setMinLines(1);input.setMaxLines(4);activeChatInput=input;LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-2,1);ip.setMargins(dp(8),0,dp(8),0);composer.addView(input,ip);
        Button send=iconButton("➤");send.setTextColor(BG);send.setBackground(makeBg(LIME,999,LIME));send.setOnClickListener(v->sendActiveChat());composer.addView(send,new LinearLayout.LayoutParams(dp(48),dp(48)));content.addView(composer);
        TextView mediaHint=tv("＋ Photo · conversation synchronisée avec le site",10,false,MUTED);mediaHint.setPadding(dp(54),dp(5),0,dp(6));content.addView(mediaHint);
        io.execute(()->{try{SyncApi.post(this,"/api/communication",store.token(),new JSONObject().put("action","read_channel").put("channel",channel));}catch(Exception ignored){}});
    }

    private void sendActiveChat(){String channel=activeChatChannel;if(channel.isEmpty()||activeChatInput==null)return;String message=activeChatInput.getText().toString().trim();if(message.isEmpty())return;activeChatDraft="";activeChatInput.setText("");io.execute(()->{try{SyncApi.post(this,"/api/communication",store.token(),new JSONObject().put("action","send").put("channel",channel).put("message",message));loadActiveChannel(true);}catch(Exception e){runOnUiThread(()->{activeChatDraft=message;if(activeChatInput!=null)activeChatInput.setText(message);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});}

    private void uploadChatImage(Uri uri){final String channel=activeChatChannel;if(channel.isEmpty())return;Toast.makeText(this,"Envoi de la photo…",Toast.LENGTH_SHORT).show();io.execute(()->{try{String mime=getContentResolver().getType(uri);if(mime==null||!mime.startsWith("image/"))throw new java.io.IOException("Format image non reconnu");String name=fileName(uri);byte[] bytes;try(java.io.InputStream in=getContentResolver().openInputStream(uri);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){if(in==null)throw new java.io.IOException("Photo inaccessible");byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>8*1024*1024)throw new java.io.IOException("La photo dépasse 8 Mo");}bytes=out.toByteArray();}JSONObject uploaded=ApiClient.postMultipart("/api/media",store.token(),bytes,name,mime);String url=uploaded.optString("url","");if(url.isEmpty())throw new java.io.IOException("URL photo absente");SyncApi.post(this,"/api/communication",store.token(),new JSONObject().put("action","send").put("channel",channel).put("message","[PHOTO] "+url));loadActiveChannel(true);}catch(Exception e){try{queueChatImageForSync(uri,channel,e);runOnUiThread(()->Toast.makeText(this,"Photo conservée sur le téléphone · envoi automatique au site en attente",Toast.LENGTH_LONG).show());}catch(Exception q){runOnUiThread(()->Toast.makeText(this,"Photo impossible : "+e.getMessage(),Toast.LENGTH_LONG).show());}}});}

    private void loadChatImage(String media,ImageView iv){io.execute(()->{try{java.net.URL url=new java.net.URL(media.startsWith("http")?media:AppConfig.BASE_URL+media);java.net.HttpURLConnection c=(java.net.HttpURLConnection)url.openConnection();c.setConnectTimeout(10000);c.setReadTimeout(15000);if(!store.token().isEmpty())c.setRequestProperty("Cookie",store.token());try(java.io.InputStream in=c.getInputStream()){Bitmap bmp=android.graphics.BitmapFactory.decodeStream(in);if(bmp!=null)runOnUiThread(()->iv.setImageBitmap(bmp));}}catch(Exception ignored){}});}
    private String shortTime(String raw){try{java.time.Instant ins=java.time.Instant.parse(raw);java.time.ZonedDateTime z=ins.atZone(java.time.ZoneId.systemDefault());return String.format(Locale.FRANCE,"%02d:%02d",z.getHour(),z.getMinute());}catch(Exception e){return "";}}

        private void renderMemberships(){
        content.addView(sectionIntro("ESPACE MEMBRE", "Adhésions", "Adhésion annuelle réellement synchronisée avec le site : demande, validation, période et paiement."));
        loadJson("/api/member", data -> {
            JSONArray arr=data.optJSONArray("memberships");
            LinearLayout summary=card();summary.addView(tv("Mon statut",20,true,TEXT));
            JSONObject member=data.optJSONObject("member");
            summary.addView(infoRow("Compte",member==null?"Non enregistré":prettyKey(member.optString("status","pending"))));
            if(arr==null||arr.length()==0){
                summary.addView(tv("Aucune adhésion annuelle n’est enregistrée pour ce compte.",13,false,MUTED));
                Button b=button("Demander mon adhésion annuelle");
                b.setOnClickListener(v->{b.setEnabled(false);io.execute(()->{try{JSONObject body=new JSONObject().put("action","request_membership");JSONObject r=SyncApi.post(this,"/api/member",store.token(),body);runOnUiThread(()->{Toast.makeText(this,r.optBoolean("_queued",false)?"Demande conservée · envoi au site en attente":"Demande d’adhésion envoyée au site",Toast.LENGTH_LONG).show();content.removeAllViews();renderMemberships();});}catch(Exception e){runOnUiThread(()->{b.setEnabled(true);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});});
                summary.addView(b);content.addView(summary);
            }else{
                JSONObject current=null;for(int i=0;i<arr.length();i++){JSONObject m=arr.optJSONObject(i);if(m!=null&&(current==null||"active".equals(m.optString("status"))))current=m;}
                if(current!=null){summary.addView(statusPill(current.optString("status","pending")));summary.addView(infoRow("Formule",current.optString("planName","Adhésion annuelle")));summary.addView(infoRow("Période",current.optString("startsAt","—")+" → "+current.optString("endsAt","—")));summary.addView(infoRow("Montant",String.format(Locale.FRANCE,"%.2f €",current.optDouble("price",150))));summary.addView(infoRow("Paiement",prettyKey(current.optString("paymentStatus","to_pay_on_site"))));}
                content.addView(summary);
                LinearLayout benefits=card();benefits.addView(tv("Avantages membre",19,true,TEXT));benefits.addView(tv("• Historique d’adhésion centralisé\n• Statut visible dans l’application et sur le site\n• Validation administrateur traçable\n• Paiement sur place selon les règles de l’association",13,false,MUTED));content.addView(benefits);
            }
            // Si le compte est administrateur, afficher aussi les demandes à valider.
            prefetchIo.execute(()->{try{JSONObject admin=ApiClient.get("/api/members",store.token());JSONArray all=admin.optJSONArray("memberships");runOnUiThread(()->{if(!"Adhésions".equals(section)||all==null)return;LinearLayout c=card();c.addView(tv("Administration · demandes d’adhésion",20,true,ORANGE));int shown=0;for(int i=0;i<all.length()&&shown<50;i++){JSONObject m=all.optJSONObject(i);if(m==null||!"pending".equals(m.optString("status")))continue;shown++;JSONObject who=m.optJSONObject("member");LinearLayout rowc=card();rowc.addView(tv(who==null?"Membre":who.optString("displayName","Membre"),16,true,TEXT));rowc.addView(tv(m.optString("planName","Adhésion annuelle")+" · "+m.optString("startsAt",""),12,false,MUTED));LinearLayout actions=row();Button ok=button("Valider");Button no=secondaryButton("Refuser");final String mid=m.optString("id","");ok.setOnClickListener(v->updateMembershipStatus(mid,"active"));no.setOnClickListener(v->updateMembershipStatus(mid,"rejected"));actions.addView(ok,new LinearLayout.LayoutParams(0,dp(46),1));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,dp(46),1);np.setMargins(dp(8),0,0,0);actions.addView(no,np);rowc.addView(actions);c.addView(rowc);}if(shown==0)c.addView(tv("Aucune demande en attente.",13,false,MUTED));content.addView(c);});}catch(Exception ignored){}});
        });
    }

        private void renderGallery(){
        LinearLayout head=row();LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.addView(tv("Médias",28,true,TEXT));copy.addView(tv("Instagram × TikTok · Unité Paintball",13,false,MUTED));head.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        Button add=orangeButton("＋ Publier");add.setOnClickListener(v->showMediaPublishChooser());head.addView(add,new LinearLayout.LayoutParams(dp(120),dp(50)));content.addView(head);

        HorizontalScrollView hsv=new HorizontalScrollView(this);hsv.setHorizontalScrollBarEnabled(false);LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);
        String[] names={"Pour toi","Grille","Vidéos","Mes publications","Favoris"};
        for(String n:names){TextView t=tabLabel(n,n.equals(galleryMode));t.setPadding(dp(16),0,dp(16),0);t.setOnClickListener(v->{galleryMode=n;render();});tabs.addView(t,new LinearLayout.LayoutParams(-2,dp(50)));}
        hsv.addView(tabs);content.addView(hsv);

        LinearLayout publishHelp=card();publishHelp.addView(tv("Publier depuis ton téléphone",17,true,TEXT));publishHelp.addView(tv("Photo ou vidéo · galerie Android · envoi au site · cache local si le réseau coupe",12,false,MUTED));
        LinearLayout quick=row();Button p=secondaryButton("▧ Photo");p.setOnClickListener(v->openGalleryPicker(false));Button vi=secondaryButton("▶ Vidéo");vi.setOnClickListener(v->openGalleryPicker(true));quick.addView(p,new LinearLayout.LayoutParams(0,dp(48),1));quick.addView(vi,new LinearLayout.LayoutParams(0,dp(48),1));publishHelp.addView(quick);content.addView(publishHelp);

        loadJson("/api/content", data -> {
            JSONArray gallery=data.optJSONArray("gallery");
            if(gallery==null||gallery.length()==0){content.addView(cardText("Aucun média publié","Les nouvelles photos et vidéos apparaîtront ici après validation."));return;}
            ArrayList<JSONObject> filtered=new ArrayList<>();
            JSONObject me=snapshot==null?null:snapshot.optJSONObject("user");String myId=me==null?"":me.optString("id",me.optString("userId",""));
            for(int i=0;i<gallery.length();i++){
                JSONObject g=gallery.optJSONObject(i);if(g==null)continue;String media=g.optString("mediaUrl",g.optString("url",""));String id=mediaIdentity(g,media);
                if("Vidéos".equals(galleryMode)&&!isVideoMedia(g,media))continue;
                if("Favoris".equals(galleryMode)&&!isFavorite(id))continue;
                if("Mes publications".equals(galleryMode)){if(myId.isEmpty())continue;String owner=g.optString("userId",g.optString("authorId",g.optString("createdBy","")));if(!myId.equals(owner))continue;}
                filtered.add(g);
            }
            if(filtered.isEmpty()){content.addView(cardText("Aucun contenu dans cet onglet","Publie un média ou change de filtre."));return;}
            if("Grille".equals(galleryMode)){renderInstagramGrid(filtered);return;}
            boolean autoplay=true;
            for(int i=0;i<Math.min(filtered.size(),80);i++){JSONObject g=filtered.get(i);content.addView(buildImmersiveGalleryPost(g,autoplay&&isVideoMedia(g,g.optString("mediaUrl",g.optString("url","")))));if(isVideoMedia(g,g.optString("mediaUrl",g.optString("url",""))))autoplay=false;}
        });
    }

    private void showMediaPublishChooser(){
        new AlertDialog.Builder(this).setTitle("Publier un média").setItems(new String[]{"📷 Choisir une photo","🎬 Choisir une vidéo"},(d,w)->openGalleryPicker(w==1)).setNegativeButton("Annuler",null).show();
    }

    private void openGalleryPicker(boolean video){
        Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.addCategory(Intent.CATEGORY_OPENABLE);pick.setType(video?"video/*":"image/*");pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(pick,video?REQ_GALLERY_VIDEO:REQ_GALLERY_IMAGE);
    }

    private boolean isVideoMedia(JSONObject g,String media){
        String k=g==null?"":g.optString("kind",g.optString("type",g.optString("mimeType",""))).toLowerCase(Locale.ROOT);
        String m=media==null?"":media.toLowerCase(Locale.ROOT);return k.contains("video")||m.contains(".mp4")||m.contains(".webm")||m.contains(".mov")||m.contains(".m4v");
    }

    private String mediaIdentity(JSONObject g,String media){
        if(g!=null){String[] keys={"id","mediaId","galleryId","postId"};for(String k:keys){String v=g.optString(k,"");if(!v.isEmpty())return v;}}
        return media==null?"":media;
    }

    private void renderInstagramGrid(ArrayList<JSONObject> items){
        int index=0;while(index<items.size()){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
            for(int col=0;col<3;col++){if(index>=items.size()){Space sp=new Space(this);row.addView(sp,new LinearLayout.LayoutParams(0,dp(112),1));continue;}JSONObject g=items.get(index++);String media=g.optString("mediaUrl",g.optString("url",""));FrameLayout cell=new FrameLayout(this);cell.setBackground(makeBg(CARD,8,STROKE));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(118),1);cp.setMargins(dp(2),dp(2),dp(2),dp(2));row.addView(cell,cp);
                if(isVideoMedia(g,media)){TextView play=tv("▶",30,true,TEXT);play.setGravity(Gravity.CENTER);cell.addView(play,new FrameLayout.LayoutParams(-1,-1));}
                else loadGridImage(cell,media);
                final JSONObject selected=g;cell.setOnClickListener(v->showMediaDetail(selected));
            }content.addView(row);}
    }

    private void loadGridImage(FrameLayout target,String media){
        prefetchIo.execute(()->{try{java.io.File f=diskCache.mediaFile(media);if(!f.isFile()||f.length()==0)f=diskCache.downloadMedia(media,store.token());Bitmap bmp=android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());if(bmp!=null)runOnUiThread(()->{ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);iv.setImageBitmap(bmp);target.addView(iv,0,new FrameLayout.LayoutParams(-1,-1));});}catch(Exception ignored){}});
    }

    private void showMediaDetail(JSONObject g){
        android.app.Dialog d=new android.app.Dialog(this);LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setPadding(dp(12),dp(12),dp(12),dp(12));shell.setBackgroundColor(BG);
        ScrollView sv=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.addView(buildImmersiveGalleryPost(g,true));Button close=button("Fermer");close.setOnClickListener(v->d.dismiss());body.addView(close);sv.addView(body);shell.addView(sv,new LinearLayout.LayoutParams(-1,-1));d.setContentView(shell);android.view.Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(BG));w.setLayout(-1,-1);}d.setOnShowListener(x->{android.view.Window ww=d.getWindow();if(ww!=null)ww.setLayout(-1,-1);});d.show();
    }

    private void loadGalleryVideo(LinearLayout card,String mediaPath,boolean autoPlay){
        FrameLayout holder=new FrameLayout(this);holder.setBackgroundColor(Color.BLACK);int h=Math.max(dp(300),(int)(getResources().getDisplayMetrics().heightPixels*.58f));card.addView(holder,new LinearLayout.LayoutParams(-1,h));
        ProgressBar spinner=new ProgressBar(this);FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.CENTER);holder.addView(spinner,sp);
        prefetchIo.execute(()->{try{java.io.File f=diskCache.mediaFile(mediaPath);if(!f.isFile()||f.length()==0)f=diskCache.downloadMedia(mediaPath,store.token());final java.io.File ff=f;runOnUiThread(()->{holder.removeAllViews();VideoView vv=new VideoView(this);vv.setVideoURI(Uri.fromFile(ff));vv.setOnPreparedListener(mp->{mp.setLooping(true);if(autoPlay){mp.setVolume(0f,0f);vv.start();}});vv.setOnClickListener(v->{if(vv.isPlaying())vv.pause();else vv.start();});holder.addView(vv,new FrameLayout.LayoutParams(-1,-1));TextView badge=tv("VIDÉO · toucher pour lecture/pause",11,true,TEXT);badge.setBackground(makeBg(Color.argb(150,0,0,0),8,0));FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,dp(34),Gravity.TOP|Gravity.LEFT);bp.setMargins(dp(10),dp(10),0,0);holder.addView(badge,bp);});}catch(Exception e){runOnUiThread(()->{holder.removeAllViews();TextView err=tv("Vidéo indisponible hors ligne",13,true,MUTED);err.setGravity(Gravity.CENTER);holder.addView(err,new FrameLayout.LayoutParams(-1,-1));});}});
    }

    private boolean isFavorite(String id){if(id==null||id.isEmpty())return false;return getSharedPreferences("up_social",MODE_PRIVATE).getStringSet("favorites",new HashSet<>()).contains(id);}
    private void toggleFavorite(String id){if(id==null||id.isEmpty())return;HashSet<String> set=new HashSet<>(getSharedPreferences("up_social",MODE_PRIVATE).getStringSet("favorites",new HashSet<>()));if(set.contains(id))set.remove(id);else set.add(id);getSharedPreferences("up_social",MODE_PRIVATE).edit().putStringSet("favorites",set).apply();}
    private void shareMedia(String media,String titleText){Intent s=new Intent(Intent.ACTION_SEND);s.setType("text/plain");String u=media==null?"":(media.startsWith("http")?media:AppConfig.BASE_URL+media);s.putExtra(Intent.EXTRA_TEXT,(titleText==null?"":titleText+"\n")+u);startActivity(Intent.createChooser(s,"Partager la publication"));}
    private void askGalleryComment(String id){final EditText input=editField("Ton commentaire","");new AlertDialog.Builder(this).setTitle("Ajouter un commentaire").setView(input).setPositiveButton("Publier",(d,w)->sendGallerySocial("comment",id,input.getText().toString().trim())).setNegativeButton("Annuler",null).show();}
    private void optimisticGalleryLike(Button button, TextView countView, String id, boolean wasLiked, int oldLikes){
        boolean nowLiked=!wasLiked;int next=Math.max(0,oldLikes+(nowLiked?1:-1));button.setText((nowLiked?"♥ ":"♡ ")+(next>0?next+" J’aime":"J’aime"));button.setTextColor(nowLiked?ORANGE:TEXT);if(countView!=null)countView.setText(next>0?"♥ "+next+" J’aime":"Soyez le premier à aimer");button.setEnabled(false);
        io.execute(()->{try{JSONObject b=new JSONObject().put("action","like").put("galleryId",id);JSONObject result=SyncApi.post(this,"/api/gallery-social",store.token(),b);runOnUiThread(()->{button.setEnabled(true);if(result.optBoolean("_queued",false))Toast.makeText(this,"J’aime mis à jour · synchronisation en attente",Toast.LENGTH_SHORT).show();refresh(false);});}catch(Exception e){runOnUiThread(()->{button.setEnabled(true);Toast.makeText(this,"Synchronisation du J’aime impossible : "+e.getMessage(),Toast.LENGTH_LONG).show();refresh(false);});}});
    }

    private void sendGallerySocial(String action,String id,String text){
        if(id==null||id.isEmpty()){Toast.makeText(this,"Identifiant du média indisponible.",Toast.LENGTH_LONG).show();return;}
        io.execute(()->{try{JSONObject b=new JSONObject().put("action",action).put("galleryId",id);if(!text.isEmpty())b.put("body",text);JSONObject result=SyncApi.post(this,"/api/gallery-social",store.token(),b);runOnUiThread(()->{String msg="like".equals(action)?(result.optBoolean("liked",false)?"J’aime ajouté.":"J’aime retiré."):"Commentaire publié.";if(result.optBoolean("_queued",false))msg="Action enregistrée sur le téléphone · envoi en attente.";Toast.makeText(this,msg,Toast.LENGTH_SHORT).show();render();});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Interaction indisponible : "+e.getMessage(),Toast.LENGTH_LONG).show());}});
    }

    private void loadGalleryImage(LinearLayout card,String mediaPath){
        FrameLayout holder=new FrameLayout(this);holder.setBackgroundColor(Color.BLACK);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(320));hp.setMargins(0,dp(8),0,0);card.addView(holder,hp);
        ProgressBar spinner=new ProgressBar(this);holder.addView(spinner,new FrameLayout.LayoutParams(dp(38),dp(38),Gravity.CENTER));
        prefetchIo.execute(()->{
            try{
                java.io.File f=diskCache.mediaFile(mediaPath);
                if(!f.isFile()||f.length()==0)f=diskCache.downloadMedia(mediaPath,store.token());
                Bitmap bmp=android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
                if(bmp!=null)runOnUiThread(()->{
                    holder.removeAllViews();ImageView iv=new ImageView(this);iv.setAdjustViewBounds(true);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);iv.setImageBitmap(bmp);holder.addView(iv,new FrameLayout.LayoutParams(-1,-1));
                    int w=Math.max(1,getResources().getDisplayMetrics().widthPixels-dp(60));int target=Math.max(dp(180),Math.min(dp(620),(int)(w*(bmp.getHeight()/(float)Math.max(1,bmp.getWidth())))));LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)holder.getLayoutParams();lp.height=target;holder.setLayoutParams(lp);
                });
            }catch(Exception e){runOnUiThread(()->{holder.removeAllViews();TextView err=tv("Image indisponible hors ligne",13,true,MUTED);err.setGravity(Gravity.CENTER);holder.addView(err,new FrameLayout.LayoutParams(-1,-1));});}
        });
    }

    private void uploadGalleryImage(Uri uri){uploadGalleryMedia(uri,false);}
    private void uploadGalleryVideo(Uri uri){uploadGalleryMedia(uri,true);}

    private void uploadGalleryMedia(Uri uri,boolean video){
        Toast.makeText(this,video?"Envoi de la vidéo…":"Envoi de la photo…",Toast.LENGTH_SHORT).show();
        io.execute(()->{
            try{
                String mime=getContentResolver().getType(uri);if(mime==null)mime=video?"video/mp4":"image/jpeg";
                String name=fileName(uri);int limit=video?80*1024*1024:12*1024*1024;byte[] bytes=readUriBytes(uri,limit);
                JSONObject uploaded=ApiClient.postMultipart("/api/media",store.token(),bytes,name,mime);
                String mediaUrl=uploaded.optString("url","");if(mediaUrl.isEmpty())throw new java.io.IOException("Le serveur n'a pas renvoyé l'adresse du média");
                JSONObject submit=new JSONObject().put("type","gallery_submission").put("kind",video?"video":"photo").put("mediaUrl",mediaUrl).put("title",name).put("album","Publications des participants");
                JSONObject result=SyncApi.post(this,"/api/content",store.token(),submit);
                runOnUiThread(()->{Toast.makeText(this,result.optString("message",video?"Vidéo envoyée.":"Photo envoyée."),Toast.LENGTH_LONG).show();section="Galerie";galleryMode=video?"Vidéos":"Pour toi";content.removeAllViews();renderGallery();});
            }catch(Exception e){try{queueGalleryMediaForSync(uri,video,e);runOnUiThread(()->Toast.makeText(this,"Média conservé sur le téléphone · publication en attente de synchronisation",Toast.LENGTH_LONG).show());}catch(Exception q){runOnUiThread(()->Toast.makeText(this,"Envoi impossible : "+e.getMessage(),Toast.LENGTH_LONG).show());}}
        });
    }

    private void queueGalleryMediaForSync(Uri uri,boolean video,Exception cause)throws Exception{String mime=getContentResolver().getType(uri);String name=fileName(uri);JSONObject follow=new JSONObject().put("type","gallery_submission").put("kind",video?"video":"photo").put("mediaUrl",MediaSyncQueue.MEDIA_URL_PLACEHOLDER).put("title",name).put("album","Publications des participants");MediaSyncQueue.enqueue(this,readUriBytes(uri,video?80*1024*1024:12*1024*1024),name,mime,"/api/content",follow,cause==null?"":cause.getMessage());}
    private byte[] readUriBytes(Uri uri,int maxBytes)throws Exception{try(java.io.InputStream in=getContentResolver().openInputStream(uri);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){if(in==null)throw new java.io.IOException("Fichier inaccessible");byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>maxBytes)throw new java.io.IOException("Le fichier dépasse la taille autorisée");}return out.toByteArray();}}
    private void queueChatImageForSync(Uri uri,String channel,Exception cause)throws Exception{String mime=getContentResolver().getType(uri);String name=fileName(uri);JSONObject follow=new JSONObject().put("action","send").put("channel",channel).put("message","[PHOTO] "+MediaSyncQueue.MEDIA_URL_PLACEHOLDER);MediaSyncQueue.enqueue(this,readUriBytes(uri,8*1024*1024),name,mime,"/api/communication",follow,cause==null?"":cause.getMessage());}
    private void queueGalleryImageForSync(Uri uri,Exception cause)throws Exception{queueGalleryMediaForSync(uri,false,cause);}
    private void queueAvatarImageForSync(Uri uri,Exception cause)throws Exception{JSONObject p=snapshot==null?null:snapshot.optJSONObject("profile");if(p==null)throw new java.io.IOException("Profil indisponible");String mime=getContentResolver().getType(uri),name=fileName(uri);JSONObject follow=buildProfilePayload(p,"photo",p.optString("avatarPreset","ranger"),MediaSyncQueue.MEDIA_URL_PLACEHOLDER);MediaSyncQueue.enqueue(this,readUriBytes(uri,8*1024*1024),name,mime,"/api/platform",follow,cause==null?"":cause.getMessage());}

    private String fileName(Uri uri){
        String name="photo.jpg";
        try(Cursor cursor=getContentResolver().query(uri,null,null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()){int idx=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(idx>=0)name=cursor.getString(idx);}
        }catch(Exception ignored){}
        return name==null||name.trim().isEmpty()?"photo.jpg":name;
    }


    private void loadAssetImage(ImageView view,String assetPath){try(java.io.InputStream in=getAssets().open(assetPath)){view.setImageBitmap(android.graphics.BitmapFactory.decodeStream(in));}catch(Exception ignored){}}

    private void loadRemoteImage(ImageView target,String mediaPath){
        prefetchIo.execute(()->{
            try{
                java.io.File f=diskCache.mediaFile(mediaPath);
                if(!f.isFile()||f.length()==0)f=diskCache.downloadMedia(mediaPath,store.token());
                Bitmap bmp=android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
                if(bmp!=null)runOnUiThread(()->target.setImageBitmap(bmp));
            }catch(Exception ignored){}
        });
    }

    private String presetIcon(String preset){
        if("shield".equals(preset))return "🛡";
        if("target".equals(preset))return "🎯";
        if("forest".equals(preset))return "🌲";
        return "🥷";
    }

    private void showAvatarPresetDialog(JSONObject profile){
        String[] labels={"🥷 Ranger","🛡 Bouclier","🎯 Cible","🌲 Forêt"};
        String[] ids={"ranger","shield","target","forest"};
        new AlertDialog.Builder(this).setTitle("Choisir une icône").setItems(labels,(d,which)->saveAvatarProfile(profile,"preset",ids[which],"")).setNegativeButton("Annuler",null).show();
    }

    private JSONObject buildProfilePayload(JSONObject p,String avatarMode,String avatarPreset,String avatarUrl) throws Exception{
        JSONObject payload=new JSONObject();
        payload.put("action","save_profile");
        payload.put("firstName",p.optString("firstName"));payload.put("lastName",p.optString("lastName"));payload.put("nickname",p.optString("nickname"));payload.put("phone",p.optString("phone"));payload.put("birthDate",p.optString("birthDate"));payload.put("address",p.optString("address"));payload.put("postalCode",p.optString("postalCode"));payload.put("city",p.optString("city"));payload.put("emergencyContact",p.optString("emergencyContact"));payload.put("level",p.optString("level","debutant"));payload.put("equipmentMode",p.optString("equipmentMode","location"));payload.put("marketingConsent",p.optBoolean("marketingConsent",false));payload.put("gamePreferences",p.optJSONArray("gamePreferences")!=null?p.optJSONArray("gamePreferences"):new JSONArray());payload.put("identityDocumentType",p.optString("identityDocumentType","none"));payload.put("avatarMode",avatarMode);payload.put("avatarPreset",avatarPreset);payload.put("avatarUrl",avatarUrl);
        return payload;
    }

    private void saveAvatarProfile(JSONObject p,String mode,String preset,String url){
        Toast.makeText(this,"Enregistrement de l’avatar…",Toast.LENGTH_SHORT).show();
        io.execute(()->{try{SyncApi.post(this,"/api/platform",store.token(),buildProfilePayload(p,mode,preset,url));runOnUiThread(()->{Toast.makeText(this,"Avatar enregistré.",Toast.LENGTH_SHORT).show();refresh(true);});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Impossible d’enregistrer l’avatar : "+e.getMessage(),Toast.LENGTH_LONG).show());}});
    }

    private void uploadAvatarImage(Uri uri){
        Toast.makeText(this,"Envoi de la photo de profil…",Toast.LENGTH_SHORT).show();
        io.execute(()->{
            try{
                String mime=getContentResolver().getType(uri);String name=fileName(uri);byte[] bytes;
                try(java.io.InputStream in=getContentResolver().openInputStream(uri);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){if(in==null)throw new java.io.IOException("Photo inaccessible");byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>8*1024*1024)throw new java.io.IOException("La photo dépasse 8 Mo");}bytes=out.toByteArray();}
                JSONObject uploaded=ApiClient.postMultipart("/api/media",store.token(),bytes,name,mime);String mediaUrl=uploaded.optString("url","");if(mediaUrl.isEmpty())throw new java.io.IOException("Le serveur n’a pas renvoyé l’adresse de la photo");
                JSONObject p=snapshot.optJSONObject("profile");if(p==null)throw new java.io.IOException("Profil indisponible");SyncApi.post(this,"/api/platform",store.token(),buildProfilePayload(p,"photo",p.optString("avatarPreset","ranger"),mediaUrl));
                runOnUiThread(()->{Toast.makeText(this,"Photo de profil enregistrée.",Toast.LENGTH_SHORT).show();refresh(true);});
            }catch(Exception e){try{queueAvatarImageForSync(uri,e);runOnUiThread(()->Toast.makeText(this,"Photo de profil conservée · envoi automatique au site en attente",Toast.LENGTH_LONG).show());}catch(Exception q){runOnUiThread(()->Toast.makeText(this,"Envoi impossible : "+e.getMessage(),Toast.LENGTH_LONG).show());}}
        });
    }

    private void renderPlayers(){
        LinearLayout intro=new LinearLayout(this);intro.setOrientation(LinearLayout.VERTICAL);intro.setBackgroundColor(BG);intro.setPadding(dp(4),dp(18),dp(4),dp(14));
        intro.addView(tv("Joueurs et amis",30,true,TEXT));intro.addView(tv("Trouve un joueur, ajoute-le et démarre une conversation.",14,false,MUTED));
        EditText search=editField("Nom, pseudo ou ville","");search.setBackground(makeBg(CARD_2,999,STROKE));intro.addView(search);content.addView(intro);

        LinearLayout playerList=new LinearLayout(this);playerList.setOrientation(LinearLayout.VERTICAL);content.addView(playerList);
        boolean hasCachedPlayers=renderCachedPlayers(playerList);
        ProgressBar pb=null;
        if(!hasCachedPlayers){pb=new ProgressBar(this);playerList.addView(pb);}
        final ProgressBar loading=pb;
        prefetchIo.execute(()->{
            try{
                JSONObject data=ApiClient.get("/api/friends",store.token());
                JSONArray rows=extractArray(data,"people","players","members","users","friends");
                // Certains comptes admin reçoivent une vue plus complète via /api/admin-players.
                if(rows==null || rows.length()==0){
                    try{
                        JSONObject adminData=ApiClient.get("/api/admin-players",store.token());
                        JSONArray adminRows=extractArray(adminData,"players","people","members","users");
                        if(adminRows!=null && adminRows.length()>0){ data=adminData; rows=adminRows; }
                    }catch(Exception ignored){}
                }
                final JSONObject cachedData=data; final JSONArray finalRows=rows;
                store.savePlayersCache(cachedData.toString());
                runOnUiThread(()->{
                    if(!"Joueurs".equals(section)&&!"Amis".equals(section))return;
                    playerList.removeAllViews();
                    appendPlayerCards(playerList,finalRows);
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    if(!"Joueurs".equals(section)&&!"Amis".equals(section))return;
                    if(loading!=null)playerList.removeView(loading);
                    if(playerList.getChildCount()==0)playerList.addView(cardText("Joueurs indisponibles","Impossible d’actualiser pour le moment. Les données en cache restent utilisées dès qu’elles sont disponibles."));
                });
            }
        });
    }

    private boolean renderCachedPlayers(LinearLayout target){
        String cached=store.playersCache();
        if(cached==null||cached.isEmpty())return false;
        try{
            JSONObject data=new JSONObject(cached);JSONArray rows=extractArray(data,"people","players","members","users","friends");
            if(rows==null||rows.length()==0)return false;
            appendPlayerCards(target,rows);return true;
        }catch(Exception ignored){return false;}
    }

    private void appendPlayerCards(LinearLayout target, JSONArray rows){
        if(rows==null||rows.length()==0){target.addView(cardText("Aucun joueur","Les joueurs apparaîtront ici."));return;}
        int rendered=0;
        for(int i=0;i<Math.min(rows.length(),100);i++){
            JSONObject x=rows.optJSONObject(i); if(x==null)continue;
            JSONObject friendshipForFilter=x.optJSONObject("friendship");
            if("Amis".equals(section) && (friendshipForFilter==null || !"accepted".equals(friendshipForFilter.optString("status","")))) continue;
            rendered++;
            JSONObject member=x.optJSONObject("member"), profile=x.optJSONObject("profile");
            if(profile==null)profile=x.optJSONObject("playerProfile");
            String name=x.optString("name","");
            if(name.isEmpty()&&profile!=null)name=profile.optString("nickname",join(profile.optString("firstName"),profile.optString("lastName")));
            if(name.isEmpty()&&member!=null)name=member.optString("displayName",member.optString("name",member.optString("email","Joueur")));
            if(name.isEmpty())name=x.optString("nickname",join(x.optString("firstName"),x.optString("lastName")));
            if(name.isEmpty())name=x.optString("displayName",x.optString("email","Joueur"));
            String city=x.optString("city",profile==null?"":profile.optString("city",""));
            String avatarUrl=x.optString("avatarUrl",profile==null?"":profile.optString("avatarUrl",""));
            String userId=x.optString("userId",x.optString("id",member==null?"":member.optString("userId",member.optString("id",""))));
            LinearLayout line=card();line.setBackground(makeBg(CARD,20,STROKE));
            LinearLayout head=row();
            if(!avatarUrl.isEmpty()){
                ImageView avImg=new ImageView(this);avImg.setScaleType(ImageView.ScaleType.CENTER_CROP);avImg.setBackground(makeBg(CARD_2,999,STROKE));head.addView(avImg,new LinearLayout.LayoutParams(dp(58),dp(58)));loadRemoteImage(avImg,avatarUrl);
            }else{
                TextView av=tv(initials(name),15,true,BG);av.setGravity(Gravity.CENTER);av.setBackground(makeBg(LIME,999,LIME));head.addView(av,new LinearLayout.LayoutParams(dp(58),dp(58)));
            }
            LinearLayout cp=new LinearLayout(this);cp.setOrientation(LinearLayout.VERTICAL);cp.setPadding(dp(14),0,0,0);cp.addView(tv(name,18,true,TEXT));if(!city.isEmpty())cp.addView(tv("⌖ "+city,13,false,MUTED));
            boolean online=x.optBoolean("online",false);String lastSeen=x.optString("lastSeenAt","");TextView presence=tv(online?"● En ligne":(lastSeen.isEmpty()?"○ Hors ligne":"○ Vu récemment"),12,true,online?LIME:MUTED);cp.addView(presence);
            JSONObject friendship=x.optJSONObject("friendship");if(friendship!=null)cp.addView(tv("Amitié : "+friendship.optString("status",""),11,false,LIME));head.addView(cp,new LinearLayout.LayoutParams(0,-2,1));line.addView(head);
            final String profileUserId=userId; if(!userId.isEmpty()){Button viewProfile=secondaryButton("Voir le profil");viewProfile.setOnClickListener(v->startActivity(new Intent(this,PlayerProfileActivity.class).putExtra("userId",profileUserId)));head.setOnClickListener(v->startActivity(new Intent(this,PlayerProfileActivity.class).putExtra("userId",profileUserId)));line.addView(viewProfile,new LinearLayout.LayoutParams(-1,dp(48)));}
            LinearLayout actions=row();Button msg=orangeButton("Message");final String playerName=name; final String playerUserId=userId; msg.setOnClickListener(v->{pendingDirectUserId=playerUserId;pendingDirectName=playerName;section="Messages";render();});actions.addView(msg,new LinearLayout.LayoutParams(0,dp(48),1));
            if(friendship==null&&!userId.isEmpty()){Button add=secondaryButton("Ajouter");final String uid=userId;add.setOnClickListener(v->sendFriendRequest(uid));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(48),1);ap.setMargins(dp(10),0,0,0);actions.addView(add,ap);}
            else if(friendship!=null){String fs=friendship.optString("status","");String fid=friendship.optString("id","");boolean incoming=friendship.optBoolean("incoming",false);if("pending".equals(fs)&&incoming){Button accept=secondaryButton("Accepter");accept.setOnClickListener(v->updateFriendship(fid,"accept"));actions.addView(accept,new LinearLayout.LayoutParams(0,dp(48),1));}else if("accepted".equals(fs)){Button remove=secondaryButton("Retirer");remove.setOnClickListener(v->updateFriendship(fid,"remove"));actions.addView(remove,new LinearLayout.LayoutParams(0,dp(48),1));}else if("pending".equals(fs)){Button wait=secondaryButton("En attente");wait.setEnabled(false);actions.addView(wait,new LinearLayout.LayoutParams(0,dp(48),1));}}line.addView(actions);
            target.addView(line);
        }
        if(rendered==0 && "Amis".equals(section)) target.addView(cardText("Aucun ami pour le moment","Les amis acceptés apparaîtront ici avec leur statut En ligne / Vu récemment."));
    }

    private void sendFriendRequest(String userId){
        io.execute(()->{try{SyncApi.post(this,"/api/friends",store.token(),new JSONObject().put("recipientUserId",userId));runOnUiThread(()->{Toast.makeText(this,"Demande d’ami envoyée.",Toast.LENGTH_SHORT).show();store.savePlayersCache("");render();});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}});
    }

    private void updateFriendship(String id,String action){
        if(id==null||id.isEmpty())return;io.execute(()->{try{ApiClient.patch("/api/friends",store.token(),new JSONObject().put("id",id).put("action",action));runOnUiThread(()->{Toast.makeText(this,"accept".equals(action)?"Ami ajouté":"Amitié retirée",Toast.LENGTH_SHORT).show();store.savePlayersCache("");render();});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}});
    }

    private void prefetchPlayers(){
        if(store==null||store.token().isEmpty())return;
        prefetchIo.execute(()->{
            try{JSONObject data=ApiClient.get("/api/friends",store.token());JSONArray rows=extractArray(data,"people","players","members","users","friends");if(rows!=null&&rows.length()>0)store.savePlayersCache(data.toString());}
            catch(Exception ignored){}
        });
    }

    private void renderNotifications(){
        content.addView(sectionIntro("CENTRE DE NOTIFICATIONS","Notifications","Personnel et administration, priorités, non-lues et accès direct aux actions."));
        loadJson("/api/player-notifications", data -> appendNotificationCenter(data));
    }

    private void appendNotificationCenter(JSONObject data){
        JSONArray rows=data.optJSONArray("rows");if(rows==null)rows=new JSONArray();
        int unread=0, urgent=0, admin=0;
        for(int i=0;i<rows.length();i++){JSONObject n=rows.optJSONObject(i);if(n==null)continue;if(!n.optBoolean("read",false))unread++;if(notificationPriorityOf(n).equals("Urgent"))urgent++;if(isAdminNotification(n))admin++;}
        LinearLayout stats=row();stats.addView(stat("Non lues",String.valueOf(unread)),weight());stats.addView(stat("Urgentes",String.valueOf(urgent)),weight());stats.addView(stat("Admin",String.valueOf(admin)),weight());content.addView(stats);

        LinearLayout scope=row();
        LinearLayout.LayoutParams scopeLp=new LinearLayout.LayoutParams(0,dp(48),1);
        scopeLp.setMargins(dp(2),dp(4),dp(2),dp(8));
        Button me=notificationFilterButton("Moi",notificationScope.equals("Moi"));
        Button adm=notificationFilterButton("Administration",notificationScope.equals("Administration"));
        scope.addView(me,scopeLp);
        scope.addView(adm,new LinearLayout.LayoutParams(scopeLp));
        me.setOnClickListener(v->{notificationScope="Moi";rerenderNotifications();});
        adm.setOnClickListener(v->{notificationScope="Administration";rerenderNotifications();});
        content.addView(scope);

        LinearLayout prioritiesWrap=new LinearLayout(this);
        prioritiesWrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams pwLp=new LinearLayout.LayoutParams(-1,-2);
        pwLp.setMargins(0,0,0,dp(8));
        prioritiesWrap.setLayoutParams(pwLp);
        String[][] prRows={{"Toutes","Urgent"},{"À traiter","Infos"}};
        for(String[] labels:prRows){
            LinearLayout r=row();
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);
            p.setMargins(dp(2),0,dp(2),dp(6));
            for(String label:labels){
                Button b=notificationFilterButton(label,notificationPriority.equals(label));
                b.setOnClickListener(v->{notificationPriority=label;rerenderNotifications();});
                r.addView(b,new LinearLayout.LayoutParams(p));
            }
            prioritiesWrap.addView(r);
        }
        content.addView(prioritiesWrap);

        LinearLayout search=card();
        EditText q=editField("Rechercher une notification",notificationQuery);
        search.addView(q);
        LinearLayout sr=row();
        Button go=secondaryButton("Rechercher");
        Button unreadOnly=secondaryButton(notificationUnreadOnly?"✓ Non lues":"Non lues uniquement");
        go.setOnClickListener(v->{notificationQuery=q.getText().toString().trim();rerenderNotifications();});
        unreadOnly.setOnClickListener(v->{notificationUnreadOnly=!notificationUnreadOnly;rerenderNotifications();});
        LinearLayout.LayoutParams srp=new LinearLayout.LayoutParams(0,dp(48),1);
        srp.setMargins(dp(2),dp(4),dp(2),0);
        sr.addView(go,srp);
        sr.addView(unreadOnly,new LinearLayout.LayoutParams(srp));
        search.addView(sr);
        content.addView(search);

        LinearLayout tools=row();
        Button mark=orangeButton("Tout marquer lu");mark.setOnClickListener(v->markAllNotificationsRead());
        Button test=secondaryButton("Tester Android");test.setText("Diagnostic et test Android");test.setOnClickListener(v->showNotificationDiagnostics());
        LinearLayout.LayoutParams toolLp=new LinearLayout.LayoutParams(0,dp(48),1);
        toolLp.setMargins(dp(2),0,dp(2),dp(8));
        tools.addView(mark,toolLp);
        tools.addView(test,new LinearLayout.LayoutParams(toolLp));
        content.addView(tools);

        int shown=0;String query=notificationQuery.toLowerCase(Locale.FRANCE);
        for(int i=0;i<rows.length();i++){JSONObject n=rows.optJSONObject(i);if(n==null)continue;boolean isAdmin=isAdminNotification(n);if("Administration".equals(notificationScope)!=isAdmin)continue;if(notificationUnreadOnly&&n.optBoolean("read",false))continue;String pr=notificationPriorityOf(n);if(!"Toutes".equals(notificationPriority)&&!notificationPriority.equals(pr))continue;String hay=(n.optString("title")+" "+n.optString("body")+" "+n.optString("category")).toLowerCase(Locale.FRANCE);if(!query.isEmpty()&&!hay.contains(query))continue;
            shown++;boolean read=n.optBoolean("read",false);LinearLayout c=card();int border="Urgent".equals(pr)?ORANGE:"À traiter".equals(pr)?LIME:STROKE;c.setBackground(makeBg(CARD_2,18,border));LinearLayout top=row();top.addView(miniPill(pr,"À traiter".equals(pr)),new LinearLayout.LayoutParams(-2,-2));TextView cat=tv(prettyKey(n.optString("category","general")),11,false,MUTED);cat.setGravity(Gravity.RIGHT);top.addView(cat,new LinearLayout.LayoutParams(0,-2,1));c.addView(top);TextView h=tv((read?"":"● ")+n.optString("title","Notification"),18,!read,TEXT);h.setPadding(0,dp(10),0,0);c.addView(h);TextView body=tv(n.optString("body",""),14,false,MUTED);body.setPadding(0,dp(6),0,dp(8));c.addView(body);String target=n.optString("targetUrl","");if(!target.isEmpty())c.addView(tv("Ouvre : "+notificationTargetLabel(target),11,false,MUTED));LinearLayout actions=row();Button open=button("Ouvrir");final JSONObject item=n;open.setOnClickListener(v->openNotification(item));actions.addView(open,new LinearLayout.LayoutParams(0,dp(48),1));if(!read){Button rd=secondaryButton("Marquer lue");String id=n.optString("id");rd.setOnClickListener(v->markNotificationRead(id));actions.addView(rd,new LinearLayout.LayoutParams(0,dp(48),1));}c.addView(actions);content.addView(c);
        }
        if(shown==0)content.addView(cardText("Rien dans cette vue","Modifie les filtres ou attends une nouvelle notification."));
    }

    private Button notificationFilterButton(String label,boolean selected){Button b=selected?orangeButton(label):secondaryButton(label);b.setTextSize(11);return b;}
    private void rerenderNotifications(){if(!"Notifications".equals(section))return;content.removeAllViews();renderNotifications();}
    private boolean isAdminNotification(JSONObject n){String c=n.optString("category","").toLowerCase(Locale.FRANCE),u=n.optString("targetUrl","").toLowerCase(Locale.FRANCE);return c.contains("admin")||u.startsWith("/admin")||c.contains("stock")||c.contains("incident")||c.contains("moderation");}
    private String notificationPriorityOf(JSONObject n){String x=(n.optString("category")+" "+n.optString("title")+" "+n.optString("body")).toLowerCase(Locale.FRANCE);if(x.contains("urgent")||x.contains("incident")||x.contains("sécurité")||x.contains("securite")||x.contains("erreur")||x.contains("annul"))return "Urgent";if(x.contains("réservation")||x.contains("reservation")||x.contains("confirmation")||x.contains("paiement")||x.contains("adhésion")||x.contains("adhesion")||x.contains("validation")||x.contains("modification")||x.contains("participation")||x.contains("admin"))return "À traiter";return "Infos";}
    private String notificationTargetLabel(String target){String s=sectionForTarget(target);return s.isEmpty()?"Détail":s;}
    private void openNotification(JSONObject n){String structured=NotificationRoutes.route(n.optString("category"),n.optString("targetUrl"));if(!structured.isEmpty()){String notificationId=n.optString("id");if(!notificationId.isEmpty())markNotificationReadSilent(notificationId);if(structured.equals("/mon-avatar")){startActivity(new Intent(this,SoldierActivity.class));return;}String nativeSection=studioSectionForDestination(structured);if(nativeSection!=null&&!nativeSection.isEmpty()&&!nativeSection.equals(structured)){section=nativeSection;render();return;}openSitePath(structured);return;}if("/mon-avatar".equals(n.optString("targetUrl"))){startActivity(new Intent(this,SoldierActivity.class));return;}String id=n.optString("id","");if(!id.isEmpty()&&!n.optBoolean("read",false))markNotificationReadSilent(id);String target=n.optString("targetUrl","");String dest=sectionForNotification(n);if(target.startsWith("/progression")||target.startsWith("/admin/gamification/collections")){openSitePath(target);return;}if(!dest.isEmpty()){section=dest;render();return;}if(target!=null&&!target.isEmpty())openSitePath(target);}
    private void markNotificationReadSilent(String id){io.execute(()->{try{SyncApi.post(this,"/api/communication",store.token(),new JSONObject().put("action","read_notification").put("notificationId",id));}catch(Exception ignored){}});}
    private String sectionForNotification(JSONObject n){
        if(n==null)return "";
        String target=n.optString("targetUrl","");
        String dest=sectionForTarget(target);
        if(!dest.isEmpty())return dest;
        String x=(n.optString("category","")+" "+n.optString("title","")+" "+n.optString("body","")).toLowerCase(Locale.FRANCE);
        if(x.contains("check-in")||x.contains("checkin")||x.contains("scanner qr")||x.contains("scan qr"))return "QR";
        if(x.contains("réservation")||x.contains("reservation")||x.contains("booking")||x.contains("créneau")||x.contains("creneau")||x.contains("participation")||x.contains("liste d'attente")||x.contains("liste d’attente"))return "Réservations";
        if(x.contains("adhésion")||x.contains("adhesion")||x.contains("membre"))return "Adhésions";
        if(x.contains("tournoi"))return "Tournois";
        if(x.contains("événement")||x.contains("evenement"))return "Événements";
        if(x.contains("incident")||x.contains("sécurité")||x.contains("securite"))return "Incidents";
        if(x.contains("stock")||x.contains("matériel")||x.contains("materiel")||x.contains("lanceur")||x.contains("masque"))return "Stocks";
        if(x.contains("finance admin")||x.contains("compta admin")||x.contains("comptabilité admin")||x.contains("comptabilite admin"))return "Finance admin";
        if(x.contains("paiement")||x.contains("acompte")||x.contains("remboursement"))return isAdminNotification(n)?"Finance admin":"Paiements";
        if(x.contains("demande d\'ami")||x.contains("demande ami")||x.contains("invitation ami")||x.contains("friend request")||x.contains("friendship"))return "Amis";
        if(x.contains("message")||x.contains("chat")||x.contains("support"))return "Messages";
        if(x.contains("photo")||x.contains("vidéo")||x.contains("video")||x.contains("galerie"))return "Galerie";
        if(x.contains("document")||x.contains("autorisation")||x.contains("charte")||x.contains("consentement"))return "Documents";
        if(x.contains("qr"))return "QR";
        return "";
    }
    private String sectionForTarget(String target){
        String t=target==null?"":target.toLowerCase(Locale.FRANCE);
        if(t.contains("checkin")||t.contains("check-in")||t.contains("/qr")||t.contains("qr-code")||t.contains("qrcode"))return "QR";
        if(t.contains("booking")||t.contains("reservation")||t.contains("confirmation")||t.contains("waitlist"))return "Réservations";
        if(t.contains("adhesion")||t.contains("member"))return "Adhésions";
        if(t.contains("/admin")&&(t.contains("payment")||t.contains("finance")||t.contains("compta")||t.contains("accounting")))return "Finance admin";
        if(t.contains("finance")||t.contains("compta")||t.contains("accounting"))return "Finance admin";
        if(t.contains("payment")||t.contains("paiement")||t.contains("deposit")||t.contains("refund"))return "Paiements";
        if(t.contains("incident"))return "Incidents";
        if(t.contains("stock")||t.contains("equipment")||t.contains("materiel"))return "Stocks";
        if(t.contains("gallery")||t.contains("media")||t.contains("photo")||t.contains("video"))return "Galerie";
        if(t.contains("tournament")||t.contains("tournoi"))return "Tournois";
        if(t.contains("event")||t.contains("evenement"))return "Événements";
        if(t.contains("player")||t.contains("joueur")||t.contains("friend"))return "Joueurs";
        if(t.contains("document")||t.contains("charte")||t.contains("autorisation"))return "Documents";
        if(t.contains("communication")||t.contains("message")||t.contains("chat"))return "Messages";
        if(t.contains("notification"))return "Notifications";
        if(t.startsWith("/admin"))return "Admin";
        return "";
    }
    private void applyNotificationIntent(Intent intent){
        if(intent==null)return;
        String target=intent.getStringExtra("target_url");
        if(target!=null&&!target.isEmpty()){String path=target;if(SharedFeaturePolicy.ownUrl(target,AppConfig.BASE_URL)){android.net.Uri u=android.net.Uri.parse(target);path=u.getEncodedPath()+(u.getEncodedQuery()!=null?"?"+u.getEncodedQuery():"");}if(path.startsWith("/")&&!path.startsWith("//")){final String destination=SharedFeaturePolicy.path(path);handler.post(()->openSharedFeature(destination));}}
        String dest=sectionForTarget(target);
        if(dest.isEmpty()){
            try{
                JSONObject n=new JSONObject();
                n.put("targetUrl",target==null?"":target);
                n.put("category",intent.getStringExtra("notification_category"));
                n.put("title",intent.getStringExtra("notification_title"));
                n.put("body",intent.getStringExtra("notification_body"));
                dest=sectionForNotification(n);
            }catch(Exception ignored){}
        }
        if(!dest.isEmpty())section=dest;
        // L'intention est consommée une seule fois : un retour au premier plan ne doit pas rouvrir
        // une ancienne notification déjà traitée.
        intent.removeExtra("target_url");
        intent.removeExtra("notification_category");
        intent.removeExtra("notification_title");
        intent.removeExtra("notification_body");
    }


    private void flushPendingNow(){
        if(store==null||store.token().isEmpty())return;
        updateSyncIndicator();
        io.execute(()->{
            int sent=SyncApi.flush(this,store.token(),50)+MediaSyncQueue.flush(this,store.token(),10);
            runOnUiThread(()->{
                Toast.makeText(this,sent>0?sent+" élément(s) synchronisé(s)":"Aucun élément envoyé pour le moment",Toast.LENGTH_LONG).show();
                updateSyncIndicator();
                if(content!=null)renderCurrentSectionFresh();
            });
        });
    }
    private void retryBlockedSyncItems(){
        int reset=SyncApi.retryBlocked(this)+MediaSyncQueue.retryBlocked(this);
        if(reset<=0){Toast.makeText(this,"Aucun élément à relancer",Toast.LENGTH_SHORT).show();updateSyncIndicator();return;}
        updateSyncIndicator();
        io.execute(()->{
            int sent=SyncApi.flush(this,store.token(),50)+MediaSyncQueue.flush(this,store.token(),10);
            runOnUiThread(()->{
                Toast.makeText(this,sent>0?sent+" élément(s) resynchronisé(s)":"Relance enregistrée · nouvel essai automatique",Toast.LENGTH_LONG).show();
                updateSyncIndicator();
                if(content!=null)renderCurrentSectionFresh();
            });
        });
    }

        private void markNotificationRead(String id){
        io.execute(()->{try{SyncApi.post(this,"/api/communication",store.token(),new JSONObject().put("action","read_notification").put("notificationId",id));runOnUiThread(this::renderCurrentSectionFresh);}catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}});
    }

    private void markAllNotificationsRead(){
        io.execute(()->{try{SyncApi.post(this,"/api/communication",store.token(),new JSONObject().put("action","read_notifications"));runOnUiThread(this::renderCurrentSectionFresh);}catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}});
    }

    private void renderCurrentSectionFresh(){
        if("Notifications".equals(section)){content.removeAllViews();renderNotifications();}
        refresh(false);
    }

    private void checkNativeNotificationsAsync(){
        if(notificationCheckBusy||store==null||store.token().isEmpty())return;
        notificationCheckBusy=true;
        prefetchIo.execute(()->{try{checkNativeNotifications();}finally{notificationCheckBusy=false;}});
    }

    private void checkNativeNotifications(){
        if(!NativeNotifications.canShow(this))return;
        try{
            JSONObject data=ApiClient.get("/api/player-notifications",store.token());
            JSONArray rows=data.optJSONArray("rows");
            if(rows==null||rows.length()==0)return;

            java.util.List<String> seenBefore=store.seenNotificationIds();
            java.util.HashSet<String> seen=new java.util.HashSet<>(seenBefore);
            ArrayList<String> pageIds=new ArrayList<>();

            // Première vérification sur ce compte : mémoriser l'existant sans faire sonner l'historique.
            if(seenBefore.isEmpty()){
                for(int i=0;i<rows.length();i++){JSONObject n=rows.optJSONObject(i);if(n==null)continue;String id=notificationIdOf(n);if(!id.isEmpty())pageIds.add(id);}
                store.rememberNotificationIds(pageIds);
                JSONObject newest=rows.optJSONObject(0);if(newest!=null)store.saveLastNotificationId(notificationIdOf(newest));
                return;
            }

            for(int i=rows.length()-1;i>=0;i--){
                JSONObject n=rows.optJSONObject(i);if(n==null)continue;
                String id=notificationIdOf(n);if(id.isEmpty())continue;
                pageIds.add(id);
                if(seen.contains(id)||n.optBoolean("read",false))continue;
                String cat=n.optString("category","general"), target=n.optString("targetUrl","");
                boolean urgent="Urgent".equals(notificationPriorityOf(n));
                NativeNotifications.show(this,stableNotificationId(id),n.optString("title","Unité Paintball"),n.optString("body",""),cat,target,urgent);
            }
            store.rememberNotificationIds(pageIds);
            JSONObject newest=rows.optJSONObject(0);if(newest!=null)store.saveLastNotificationId(notificationIdOf(newest));
            store.saveLastSuccessSync(System.currentTimeMillis());
        }catch(Exception ignored){}
    }

    private static String notificationIdOf(JSONObject n){
        Object raw=n==null?null:n.opt("id");
        return raw==null||raw==JSONObject.NULL?"":String.valueOf(raw);
    }

    private static int stableNotificationId(String id){
        int value=id==null?1:id.hashCode();
        if(value==Integer.MIN_VALUE)value=1;
        return Math.abs(value==0?1:value);
    }

    private void renderRewards(){
        content.addView(sectionIntro("PROGRESSION UNITÉ", "Mon niveau, mon rang et mes récompenses", "XP, Points Unité, badges, missions, saison, boutique et roulette synchronisés avec le site."));
        loadJson("/api/gamification?scope=profile", data -> {
            JSONObject progression=data.optJSONObject("progression"), level=data.optJSONObject("level"), rank=data.optJSONObject("rank"), season=data.optJSONObject("season");
            if(progression==null){content.addView(cardText("Gamification", "La migration 0055 doit être appliquée sur le serveur."));return;}
            LinearLayout heroCard=card();heroCard.setBackground(makeBg(CARD_2,18,LIME));heroCard.addView(tv("NIVEAU "+(level==null?progression.optInt("level",1):level.optInt("level",1)),12,true,LIME));
            heroCard.addView(tv(level==null?"Progression":level.optString("label","Progression"),28,true,TEXT));
            JSONObject selectedTitle=data.optJSONObject("selectedTitle");if(selectedTitle!=null)heroCard.addView(tv(selectedTitle.optString("name",""),12,true,ORANGE));
            int currentXp=level==null?0:level.optInt("currentXp",0), nextXp=Math.max(1,level==null?1000:level.optInt("nextXp",1000));
            heroCard.addView(tv(currentXp+" / "+nextXp+" XP",14,false,MUTED));
            ProgressBar xp=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);xp.setMax(nextXp);xp.setProgress(Math.min(currentXp,nextXp));LinearLayout.LayoutParams xpP=new LinearLayout.LayoutParams(-1,dp(12));xpP.setMargins(0,dp(10),0,dp(8));heroCard.addView(xp,xpP);
            LinearLayout stats=row();String rankLabel=rank==null?progression.optString("rankCode","Recrue"):rank.optString("name","Recrue")+" "+rank.optString("division","");String[][] values={{"Rang",rankLabel},{"Points Unité",String.valueOf(progression.optInt("unityPoints",0))},{"Badges",String.valueOf(data.optJSONArray("badges")==null?0:data.optJSONArray("badges").length())},{"Jetons",String.valueOf(data.optJSONArray("rouletteTokens")==null?0:data.optJSONArray("rouletteTokens").length())}};for(String[] x:values){LinearLayout c=card();c.addView(tv(x[0],11,false,MUTED));c.addView(tv(x[1],18,true,TEXT));stats.addView(c,new LinearLayout.LayoutParams(0,-2,1));}heroCard.addView(stats);content.addView(heroCard);

            JSONObject dailyRoulette=data.optJSONObject("dailyRoulette");boolean freeSpin=dailyRoulette!=null&&dailyRoulette.optBoolean("available",false);JSONArray rouletteTokens=data.optJSONArray("rouletteTokens");int bonusTokens=0;if(rouletteTokens!=null)for(int i=0;i<rouletteTokens.length();i++){JSONObject t=rouletteTokens.optJSONObject(i);if(t!=null&&"available".equals(t.optString("status","available"))&&!"daily_free".equals(t.optString("sourceType")))bonusTokens++;}
            LinearLayout actions=card();actions.setBackground(makeBg(CARD_2,18,LIME));actions.addView(tv("ROULETTE QUOTIDIENNE",12,true,ORANGE));actions.addView(tv(freeSpin?"1 TOUR GRATUIT DISPONIBLE":"TOUR QUOTIDIEN UTILISÉ",20,true,freeSpin?LIME:MUTED));actions.addView(tv(freeSpin?"Tour commun au site et à l’application.":"Le prochain tour gratuit revient au changement de journée (heure de Paris).",12,false,MUTED));Button daily=secondaryButton("🎁 Récompense de connexion");daily.setOnClickListener(v->gamificationAction("claim_daily",null));actions.addView(daily);Button spin=button(freeSpin?"🎯 TOURNER GRATUITEMENT":bonusTokens>0?"🎯 UTILISER UN JETON ("+bonusTokens+")":"🎯 REVIENS DEMAIN");spin.setEnabled(freeSpin||bonusTokens>0);spin.setOnClickListener(v->renderGamificationRoulette());actions.addView(spin);actions.addView(tv("Gros lot : maximum 1 partie offerte par semaine pour l’ensemble des joueurs.",11,true,ORANGE));Button shop=secondaryButton("🛍 Boutique Points Unité");shop.setOnClickListener(v->renderGamificationShop());actions.addView(shop);content.addView(actions);

            JSONObject report=data.optJSONObject("lastGameReport");if(report!=null){LinearLayout reportCard=card();reportCard.setBackground(makeBg(CARD_2,18,report.optBoolean("victory",false)?LIME:STROKE));reportCard.addView(tv(report.optBoolean("victory",false)?"VICTOIRE · RAPPORT DE PARTIE":"RAPPORT DE PARTIE",12,true,report.optBoolean("victory",false)?LIME:ORANGE));reportCard.addView(tv(report.optString("scenario",report.optString("name","Partie")),21,true,TEXT));reportCard.addView(infoRow("XP gagnée","+"+report.optInt("xpGained",0)));reportCard.addView(infoRow("Points Unité","+"+report.optInt("pointsGained",0)));reportCard.addView(infoRow("Points de rang","+"+report.optInt("rankPointsGained",0)));reportCard.addView(infoRow("Objectifs",String.valueOf(report.optInt("objectives",0))));content.addView(reportCard);}

            JSONArray favoriteBadges=data.optJSONArray("favoriteBadges");if(favoriteBadges!=null&&favoriteBadges.length()>0){LinearLayout fav=card();fav.addView(tv("BADGES FAVORIS",12,true,ORANGE));for(int i=0;i<Math.min(5,favoriteBadges.length());i++){JSONObject x=favoriteBadges.optJSONObject(i),b=x==null?null:x.optJSONObject("badge");if(b!=null)fav.addView(infoRow("🏅 "+b.optString("name"),b.optString("rarity","")));}content.addView(fav);}

            JSONArray badges=data.optJSONArray("badges");LinearLayout badgeCard=card();badgeCard.addView(tv("BADGES",12,true,ORANGE));badgeCard.addView(tv("Mes badges",23,true,TEXT));if(badges==null||badges.length()==0)badgeCard.addView(tv("Les badges apparaîtront après les premières actions vérifiées.",13,false,MUTED));else for(int i=0;i<Math.min(8,badges.length());i++){JSONObject x=badges.optJSONObject(i),b=x==null?null:x.optJSONObject("badge"),link=x==null?null:x.optJSONObject("link");if(b==null)continue;badgeCard.addView(infoRow((link!=null&&link.optBoolean("unlocked"))?"🏅 "+b.optString("name"):"○ "+b.optString("name"),b.optString("rarity","commun")+(link==null?"":" · "+link.optInt("progress",0)+"/"+link.optInt("progressTarget",1))));}content.addView(badgeCard);

            JSONArray missions=data.optJSONArray("missions");LinearLayout missionCard=card();missionCard.addView(tv("MISSIONS",12,true,ORANGE));missionCard.addView(tv("En cours",23,true,TEXT));if(missions==null||missions.length()==0)missionCard.addView(tv("Les missions se synchronisent automatiquement avec les actions terrain.",13,false,MUTED));else for(int i=0;i<Math.min(8,missions.length());i++){JSONObject x=missions.optJSONObject(i),m=x==null?null:x.optJSONObject("mission"),link=x==null?null:x.optJSONObject("link");if(m==null||link==null)continue;missionCard.addView(infoRow(m.optString("name"),link.optInt("progress",0)+" / "+link.optInt("target",1)));}content.addView(missionCard);

            LinearLayout seasonCard=card();seasonCard.addView(tv("SAISON",12,true,ORANGE));if(season==null)seasonCard.addView(tv("Aucune saison active",20,true,TEXT));else{seasonCard.addView(tv(season.optString("name","Saison Unité"),22,true,TEXT));seasonCard.addView(infoRow("Niveau saison",String.valueOf(season.optInt("level",1))));seasonCard.addView(infoRow("Progression",season.optInt("currentXp",0)+" / "+season.optInt("nextXp",1500)+" XP"));}content.addView(seasonCard);

            LinearLayout complete=card();complete.addView(tv("CENTRE COMPLET · 100 % NATIF",12,true,ORANGE));
            String[][] links={{"🏆 Classements","classement"},{"🎁 Mes récompenses","recompenses"},{"📦 Mes coffres","coffres"},{"🤝 Cadeaux","cadeaux"},{"📊 Statistiques","statistiques"},{"🏷 Titres & profil","titres"},{"🏅 Badges","badges"},{"🎯 Missions & défis","missions"},{"🪖 Saison & Pass Unité","saison"},{"✨ Cosmétiques","cosmetiques"},{"📜 Historique","historique"},{"📋 Rapport de partie","rapport"}};
            for(String[] link:links){Button b=secondaryButton(link[0]);String page=link[1];b.setOnClickListener(v->renderGamificationNativePage(page));complete.addView(b);}
            Button shopNative=secondaryButton("🛍 Boutique Unité");shopNative.setOnClickListener(v->renderGamificationShop());complete.addView(shopNative);
            Button rouletteNative=secondaryButton("🎯 Roulette quotidienne");rouletteNative.setOnClickListener(v->renderGamificationRoulette());complete.addView(rouletteNative);
            Button hub=button("⚡ TOUT LE CENTRE PROGRESSION");hub.setOnClickListener(v->renderGamificationNativeHub());complete.addView(hub);content.addView(complete);
        });
    }

    private void renderGamificationRoulette(){
        content.removeAllViews();
        content.addView(sectionIntro("ROULETTE UNITÉ", "Ton tour quotidien", "Un tour gratuit par jour, partagé entre le site et l’application. Le résultat est calculé et validé uniquement par le serveur."));
        loadJson("/api/gamification?scope=profile", data -> {
            JSONObject daily=data.optJSONObject("dailyRoulette");
            boolean free=daily!=null&&daily.optBoolean("available",false);
            JSONArray tokens=data.optJSONArray("rouletteTokens");
            int bonus=0;
            if(tokens!=null)for(int i=0;i<tokens.length();i++){JSONObject t=tokens.optJSONObject(i);if(t!=null&&"available".equals(t.optString("status","available"))&&!"daily_free".equals(t.optString("sourceType")))bonus++;}

            LinearLayout heroCard=card();heroCard.setGravity(Gravity.CENTER_HORIZONTAL);heroCard.setBackground(makeBg(CARD_2,22,LIME));
            TextView over=tv("RÉCOMPENSE QUOTIDIENNE",11,true,ORANGE);over.setLetterSpacing(.18f);heroCard.addView(over);
            TextView state=tv(free?"1 TOUR GRATUIT PRÊT":bonus>0?bonus+" JETON(S) BONUS":"TOUR DU JOUR UTILISÉ",22,true,free?LIME:TEXT);state.setGravity(Gravity.CENTER);state.setPadding(0,dp(8),0,dp(12));heroCard.addView(state);
            TextView wheel=tv("🎯\nROULETTE\nUNITÉ",24,true,LIME);wheel.setGravity(Gravity.CENTER);wheel.setBackground(makeBg(Color.rgb(7,24,31),999,LIME));LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(dp(220),dp(220));wp.setMargins(0,dp(12),0,dp(16));heroCard.addView(wheel,wp);
            heroCard.addView(tv("1 × PAR JOUR",12,true,ORANGE));
            heroCard.addView(tv("Gros lot : 1 partie offerte maximum par semaine pour l’ensemble des joueurs.",12,true,MUTED));
            Button spin=button(free?"TOURNER GRATUITEMENT":bonus>0?"UTILISER UN JETON ("+bonus+")":"REVIENS DEMAIN");spin.setEnabled(free||bonus>0);heroCard.addView(spin);
            spin.setOnClickListener(v->spinRouletteAnimated(wheel,spin));
            content.addView(heroCard);

            LinearLayout rules=card();rules.addView(tv("SÉCURITÉ DU TIRAGE",12,true,ORANGE));rules.addView(infoRow("Décision","Serveur uniquement"));rules.addView(infoRow("Synchronisation","Site + application"));rules.addView(infoRow("Achat aléatoire","Aucun"));rules.addView(infoRow("Partie offerte","Quota global 1 / semaine"));content.addView(rules);
            Button back=secondaryButton("Retour à ma progression");back.setOnClickListener(v->{section="Points";render();});content.addView(back);
        });
    }

    private void spinRouletteAnimated(View wheel,Button spin){
        spin.setEnabled(false);spin.setText("TIRAGE EN COURS…");
        wheel.animate().rotationBy(1440f).setDuration(1400L).start();
        final long started=System.currentTimeMillis();
        io.execute(()->{try{
            JSONObject payload=new JSONObject();payload.put("action","spin_roulette");
            JSONObject response=SyncApi.post(this,"/api/gamification",store.token(),payload);
            JSONObject result=response.optJSONObject("result"),prize=result==null?null:result.optJSONObject("prize");
            if(prize==null)throw new Exception("Résultat indisponible : consulte ton historique avant de réessayer.");
            String prizeName=prize.optString("name","Récompense obtenue");
            long wait=Math.max(0L,1450L-(System.currentTimeMillis()-started));
            handler.postDelayed(()->{
                RewardRevealDialog.show(this,true,prizeName,()->renderGamificationRoulette());
            },wait);
        }catch(Exception e){runOnUiThread(()->{spin.setEnabled(true);spin.setText("RÉESSAYER");Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});
    }

    private void gamificationAction(String action,JSONObject extra){
        io.execute(()->{try{JSONObject payload=extra==null?new JSONObject():extra;payload.put("action",action);JSONObject r=SyncApi.post(this,"/api/gamification",store.token(),payload);runOnUiThread(()->{JSONObject result=r.optJSONObject("result");String msg="Action enregistrée";if("spin_roulette".equals(action)&&result!=null){JSONObject p=result.optJSONObject("prize");if(p!=null)msg="Gagné : "+p.optString("name","Récompense");}Toast.makeText(this,msg,Toast.LENGTH_LONG).show();render();});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}});
    }

    private void renderGamificationShop(){
        content.removeAllViews();content.addView(sectionIntro("BOUTIQUE UNITÉ","Échanger mes Points Unité","Les récompenses réelles sont retirées sur place et restent tracées côté serveur."));
        loadJson("/api/gamification?scope=shop",data->{JSONArray items=data.optJSONArray("items");if(items==null||items.length()==0){content.addView(cardText("Boutique vide","Aucune récompense active."));return;}for(int i=0;i<items.length();i++){JSONObject x=items.optJSONObject(i),it=x==null?null:x.optJSONObject("item"),rw=x==null?null:x.optJSONObject("reward");if(it==null||rw==null)continue;LinearLayout c=card();c.addView(tv(rw.optString("name","Récompense"),20,true,TEXT));c.addView(tv(rw.optString("description"),13,false,MUTED));c.addView(miniPill(it.optInt("pointsCost",0)+" Points Unité",false));Button b=button("Échanger");String id=it.optString("id");b.setOnClickListener(v->{JSONObject extra=new JSONObject();try{extra.put("shopId",id);}catch(Exception ignored){}gamificationAction("buy_shop",extra);});c.addView(b);content.addView(c);}});
        Button back=secondaryButton("Retour progression");back.setOnClickListener(v->{section="Points";render();});content.addView(back);
    }

    private void redeem(String rewardId){io.execute(()->{try{JSONObject r=SyncApi.post(this,"/api/rewards",store.token(),new JSONObject().put("rewardId",rewardId));runOnUiThread(()->{Toast.makeText(this,r.optBoolean("_queued",false)?"Demande enregistrée sur le téléphone · envoi en attente":"Demande enregistrée : "+r.optString("code"),Toast.LENGTH_LONG).show();renderRewards();});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}});}

    private void renderTournaments(){
        content.addView(sectionIntro("COMPÉTITION", "Tournois", "Tournois réellement lus depuis le site, inscriptions d’équipes et création administrateur."));
        loadJson("/api/group-events", data -> {
            JSONArray tournaments=data.optJSONArray("tournaments");
            if(tournaments==null||tournaments.length()==0)content.addView(cardText("Aucun tournoi ouvert","Les prochaines compétitions apparaîtront ici."));
            else {JSONObject profile=snapshot.optJSONObject("profile"), user=snapshot.optJSONObject("user");for(int i=0;i<tournaments.length();i++){JSONObject t=tournaments.optJSONObject(i);if(t==null)continue;LinearLayout c=card();TextView badge=miniPill(t.optString("format","TOURNOI"),true);c.addView(badge,new LinearLayout.LayoutParams(-2,-2));TextView h=tv(t.optString("title","Tournoi"),21,true,TEXT);h.setPadding(0,dp(9),0,0);c.addView(h);c.addView(infoRow("Date",t.optString("tournamentDate")+" · "+t.optString("startTime")));c.addView(infoRow("Lieu",t.optString("venue","Terrain principal")));c.addView(infoRow("Équipes",t.optInt("maximumTeams",0)+" max · "+t.optInt("playersPerTeam",0)+" joueurs/équipe"));if(!t.optString("description").isEmpty()){TextView d=tv(t.optString("description"),13,false,MUTED);d.setPadding(0,dp(7),0,0);c.addView(d);}Button register=button("Inscrire mon équipe");register.setOnClickListener(v->showTournamentRegistration(t,profile,user));c.addView(register);content.addView(c);}}
        });
        prefetchIo.execute(()->{try{ApiClient.get("/api/tournaments-admin",store.token());runOnUiThread(()->{if(!"Tournois".equals(section))return;Button create=button("＋ Créer un tournoi");create.setOnClickListener(v->showCreateTournamentForm());content.addView(create);});}catch(Exception ignored){}});
    }

        private void showTournamentRegistration(JSONObject tournament,JSONObject profile,JSONObject user){
        content.removeAllViews();
        content.addView(hero("Inscription tournoi",tournament.optString("title","Tournoi")));
        content.addView(cardText("Format",tournament.optString("tournamentDate")+" · "+tournament.optString("startTime")+"\n"+tournament.optInt("playersPerTeam",0)+" joueurs par équipe"));
        String captainName=profile==null?"":join(profile.optString("firstName"),profile.optString("lastName"));
        String captainEmail=user==null?"":user.optString("email");
        String captainPhone=profile==null?"":profile.optString("phone");
        EditText teamName=editField("Nom de l'équipe","");
        EditText captain=editField("Nom du capitaine",captainName);
        EditText email=editField("E-mail du capitaine",captainEmail);email.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText phone=editField("Téléphone du capitaine",captainPhone);phone.setInputType(InputType.TYPE_CLASS_PHONE);
        EditText notes=editField("Message facultatif","");
        content.addView(teamName);content.addView(captain);content.addView(email);content.addView(phone);content.addView(notes);
        Button send=button("Envoyer l'inscription");content.addView(send);
        send.setOnClickListener(v->{
            if(teamName.getText().toString().trim().isEmpty()||captain.getText().toString().trim().isEmpty()||email.getText().toString().trim().isEmpty()||phone.getText().toString().trim().isEmpty()){
                Toast.makeText(this,"Complète les coordonnées de l'équipe.",Toast.LENGTH_LONG).show();return;
            }
            JSONObject payload=new JSONObject();
            try{
                payload.put("action","team");
                payload.put("tournamentId",tournament.optString("id"));
                payload.put("teamName",teamName.getText().toString().trim());
                payload.put("captainName",captain.getText().toString().trim());
                payload.put("captainEmail",email.getText().toString().trim());
                payload.put("captainPhone",phone.getText().toString().trim());
                payload.put("playerCount",tournament.optInt("playersPerTeam",1));
                payload.put("notes",notes.getText().toString().trim());
            }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();return;}
            send.setEnabled(false);
            io.execute(()->{try{
                SyncApi.post(this,"/api/group-events",store.token(),payload);
                runOnUiThread(()->{Toast.makeText(this,"Inscription envoyée pour validation.",Toast.LENGTH_LONG).show();section="Tournois";render();});
            }catch(Exception e){runOnUiThread(()->{send.setEnabled(true);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});
        });
        Button back=button("Retour aux tournois");back.setOnClickListener(v->{section="Tournois";render();});content.addView(back);
    }


    /** 2.2.31 : dashboard admin orienté tâches, terrain et décisions. */
    private void renderAdmin(){
        loadJson("/api/platform", data -> {
            boolean serverAdmin=data.optBoolean("administrator",false), owner=isOwnerAccount(data);
            if(!serverAdmin&&!owner){content.addView(cardText("Accès administrateur","Ce compte n'est pas identifié comme administrateur par le site."));return;}
            LinearLayout hero=card();hero.setBackground(makeBg(NeonPalette.SURFACE,22,NeonPalette.CYAN));hero.addView(tv("QG ADMIN // UNITÉ PAINTBALL",11,true,NeonPalette.CYAN));hero.addView(tv("Bonjour 👋",29,true,TEXT));hero.addView(tv("Priorité aux validations et au terrain. Le reste est rangé dans Gestion.",13,false,MUTED));Button player=secondaryButton("REVENIR À LA VUE JOUEUR");player.setOnClickListener(v->toggleAdminMode());hero.addView(player);content.addView(hero);

            JSONArray tasks=data.optJSONArray("tasks");int taskCount=tasks==null?0:tasks.length();int alerts=snapshot==null||snapshot.optJSONArray("alerts")==null?0:snapshot.optJSONArray("alerts").length();
            LinearLayout metrics=row();LinearLayout m1=adminMetric("À valider",String.valueOf(taskCount),"actions en attente");m1.setOnClickListener(v->{section="Validations admin";render();});LinearLayout m2=adminMetric("Alertes",String.valueOf(alerts),"notifications");m2.setOnClickListener(v->{section="Notifications";render();});metrics.addView(m1,weight());metrics.addView(m2,weight());content.addView(metrics);

            LinearLayout priority=card();priority.setBackground(makeBg(CARD_2,18,taskCount>0?NeonPalette.CYAN:NeonPalette.SUCCESS));priority.addView(tv(taskCount>0?"ACTION REQUISE":"TOUT EST À JOUR",11,true,taskCount>0?NeonPalette.CYAN:NeonPalette.SUCCESS));priority.addView(tv(taskCount>0?taskCount+" élément(s) attendent ta décision":"Aucune validation urgente",21,true,TEXT));Button review=button(taskCount>0?"TRAITER MAINTENANT":"VOIR LE CENTRE DE VALIDATION");review.setOnClickListener(v->{section="Validations admin";render();});priority.addView(review);content.addView(priority);

            LinearLayout today=card();today.addView(tv("AUJOURD'HUI",11,true,NeonPalette.CYAN));
            LinearLayout r1=row();r1.addView(adminUxTile("Scanner QR","Arrivées terrain",()->startActivity(new Intent(this,ScannerActivity.class))),weight());r1.addView(adminUxTile("Partie Live","Score & arbitrage",this::loadAdminLive),weight());today.addView(r1);
            LinearLayout r2=row();r2.addView(adminUxTile("Réservations","Planning",this::loadAdminBookings),weight());r2.addView(adminUxTile("Joueurs","Fiches & recherche",this::loadAdminPlayers),weight());today.addView(r2);content.addView(today);

            LinearLayout manage=card();manage.addView(tv("GESTION",11,true,MUTED));manage.addView(tv("Back-office",20,true,TEXT));Button go=secondaryButton("OUVRIR GESTION");go.setOnClickListener(v->{section="Gestion admin";render();});manage.addView(go);Button menu=secondaryButton("☰ TOUS LES OUTILS ADMIN");menu.setOnClickListener(v->showMainMenu());manage.addView(menu);content.addView(manage);
        });
    }

    private void renderAdminLegacy(){
        loadJson("/api/platform", data -> {
            boolean serverAdmin=data.optBoolean("administrator",false), owner=isOwnerAccount(data);
            if(!serverAdmin&&!owner){content.addView(cardText("Accès administrateur","Ce compte n’est pas identifié comme administrateur par le site."));return;}

            TextView ov=tv("UNITÉ PAINTBALL",12,true,LIME);ov.setLetterSpacing(.17f);ov.setPadding(0,dp(6),0,0);content.addView(ov);
            content.addView(tv("Tableau de bord",34,true,TEXT));
            TextView sub=tv("Les outils détaillés sont maintenant rangés directement dans Menu principal > Administration, avec des sous-menus repliables.",16,false,MUTED);sub.setPadding(0,dp(8),0,dp(18));content.addView(sub);

            Button openMenu=button("☰ Ouvrir le menu Administration");openMenu.setOnClickListener(v->showMainMenu());content.addView(openMenu);

            LinearLayout money=card();money.setBackground(makeBg(CARD_2,18,LIME));money.addView(tv("ARGENT À REVERSER À L’ASSOCIATION",15,true,TEXT));money.addView(tv("0,00 €",30,false,TEXT));money.addView(tv("Voir les encaissements et les reversements →",13,false,TEXT));money.setOnClickListener(v->{section="Finance admin";render();});content.addView(money);

            JSONArray tasks=data.optJSONArray("tasks");int taskCount=tasks==null?0:tasks.length();
            int alerts=snapshot==null?0:(snapshot.optJSONArray("alerts")==null?0:snapshot.optJSONArray("alerts").length());
            LinearLayout pending=adminMetric("À valider",String.valueOf(taskCount),"Comptes, réservations, modifications et adhésions");pending.setOnClickListener(v->loadAdminTasks());content.addView(pending);
            LinearLayout notifications=adminMetric("Messages et alertes",String.valueOf(alerts),"Notifications non lues");notifications.setOnClickListener(v->{section="Notifications";render();});content.addView(notifications);

            LinearLayout quick=card();quick.addView(tv("Accès rapides",20,true,TEXT));
            LinearLayout r1=row();Button scan=button("Scanner un QR");scan.setOnClickListener(v->startActivity(new Intent(this,ScannerActivity.class)));Button direct=button("Partie en direct");direct.setOnClickListener(v->loadAdminLive());LinearLayout.LayoutParams g1=new LinearLayout.LayoutParams(0,dp(54),1);g1.setMargins(0,dp(10),dp(6),0);r1.addView(scan,g1);LinearLayout.LayoutParams g2=new LinearLayout.LayoutParams(0,dp(54),1);g2.setMargins(dp(6),dp(10),0,0);r1.addView(direct,g2);quick.addView(r1);
            LinearLayout r2=row();Button players=secondaryButton("Joueurs");players.setOnClickListener(v->loadAdminPlayers());Button bookings=secondaryButton("Réservations");bookings.setOnClickListener(v->loadAdminBookings());LinearLayout.LayoutParams h1=new LinearLayout.LayoutParams(0,dp(54),1);h1.setMargins(0,dp(10),dp(6),0);r2.addView(players,h1);LinearLayout.LayoutParams h2=new LinearLayout.LayoutParams(0,dp(54),1);h2.setMargins(dp(6),dp(10),0,0);r2.addView(bookings,h2);quick.addView(r2);content.addView(quick);
        });
    }

    private LinearLayout adminMetric(String heading,String value,String sub){
        LinearLayout c=card();c.setPadding(dp(16),dp(16),dp(16),dp(16));c.addView(tv(heading,18,true,TEXT));TextView n=tv(value,38,false,NeonPalette.CYAN);n.setPadding(0,dp(10),0,dp(6));c.addView(n);c.addView(tv(sub,13,false,MUTED));return c;
    }

    private LinearLayout adminGroup(String heading,String[][] items){
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);outer.setBackground(makeBg(CARD_2,20,STROKE));outer.setPadding(dp(12),dp(12),dp(12),dp(12));LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,-2);op.setMargins(0,0,0,dp(14));outer.setLayoutParams(op);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setBackground(makeBg(CARD,18,STROKE));box.setPadding(dp(14),dp(10),dp(14),dp(10));box.addView(tv(heading,19,true,TEXT));
        for(String[] it:items){
            LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.VERTICAL);line.setPadding(dp(8),dp(16),dp(8),dp(16));line.addView(tv(it[0],17,false,TEXT));line.addView(tv(it[1],12,false,MUTED));final String action=it[2];line.setOnClickListener(v->{
                if("studio".equals(action))startActivity(new Intent(this,StudioActivity.class));
                else if("players".equals(action))loadAdminPlayers();
                else if("bookings".equals(action))loadAdminBookings();
                else if("live".equals(action))loadAdminLive();
                else if("scanner".equals(action))startActivity(new Intent(this,ScannerActivity.class));
                else if("notifications".equals(action)){section="Notifications";render();}
                else if("tournaments".equals(action)){section="Tournois";render();}
                else if("memberships".equals(action)){section="Adhésions";render();}
                else if("media".equals(action)){section="Galerie";render();}
                else if("rewards".equals(action)){section="Points";render();}
                else if("gamification".equals(action))loadAdminGeneric("Gamification","/api/admin-gamification","XP, rangs, missions, roulette et récompenses.");
                else if("arenaadmin".equals(action))loadAdminGeneric("Arena","/api/arena","Grades, équipements, matchmaking et activité Arena.");
                else if("financeapp".equals(action)){section="Finance admin";render();}
                else if("stockapp".equals(action)){section="Stocks";render();}
                else if("incidentapp".equals(action)){section="Incidents";render();}
                else if("documentsapp".equals(action)){section="Documents";render();}
                else loadAdminGeneric(it[0],adminPath(action),it[1]);
            });box.addView(line);
        }outer.addView(box);return outer;
    }

    private String adminPath(String action){
        if("validation".equals(action))return "/api/platform";
        if("newcomers".equals(action))return "/api/members";
        if("changes".equals(action))return "/api/booking-changes";
        if("blocked".equals(action))return "/api/reliability";
        if("participations".equals(action))return "/api/participations";
        if("duplicates".equals(action))return "/api/account-merge";
        if("confirmations".equals(action))return "/api/confirmation";
        if("games".equals(action))return "/api/sessions";
        if("communications".equals(action))return "/api/communication";
        if("payments".equals(action))return "/api/commercial";
        if("invoices".equals(action))return "/api/management?admin=1";
        if("stats".equals(action))return "/api/operations";
        if("gamification".equals(action))return "/api/admin-gamification";
        if("audit".equals(action))return "/api/admin-audit";
        if("backups".equals(action))return "/api/admin-backups";
        if("settings".equals(action))return "/api/admin-settings";
        if("management".equals(action))return "/api/management?admin=1";
        return "/api/platform";
    }

        private boolean isOwnerAccount(JSONObject platform) {
        JSONObject u = platform == null ? null : platform.optJSONObject("user");
        String email = u == null ? "" : u.optString("email", "").trim();
        if (email.isEmpty() && snapshot != null) {
            JSONObject su = snapshot.optJSONObject("user");
            if (su != null) email = su.optString("email", "").trim();
        }
        return OWNER_EMAIL.equalsIgnoreCase(email);
    }

    private LinearLayout adminHero(String heading, String sub) {
        LinearLayout l = card();
        TextView badge = tv("ADMIN", 11, true, BG);
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        badge.setBackground(makeBg(ORANGE, 999, ORANGE));
        l.addView(badge, new LinearLayout.LayoutParams(-2, -2));
        TextView h = tv(heading, 25, true, TEXT);
        h.setPadding(0, dp(12), 0, 0);
        l.addView(h);
        TextView st = tv(sub, 13, false, MUTED);
        st.setPadding(0, dp(5), 0, 0);
        l.addView(st);
        return l;
    }

    private void loadAdminPlayers(){
        adminSubpage=true;
        content.removeAllViews();
        content.addView(sectionIntro("ADMINISTRATION","Joueurs","Recherche et fiches joueurs — affichage instantané du cache puis actualisation serveur."));
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);content.addView(list);
        String cached=store.playersCache();
        if(cached!=null&&!cached.isEmpty())try{JSONObject d=new JSONObject(cached);appendAdminPlayerCards(list,d.optJSONArray("players"));}catch(Exception ignored){}
        if(list.getChildCount()==0)list.addView(cardText("Joueurs","Chargement…"));
        prefetchIo.execute(()->{try{JSONObject d=ApiClient.get("/api/admin-players",store.token());store.savePlayersCache(d.toString());JSONArray rows=extractArray(d,"players","people","members","users");runOnUiThread(()->{list.removeAllViews();appendAdminPlayerCards(list,rows);Button back=secondaryButton("← Retour à l’administration");back.setOnClickListener(v->{section="Admin";render();});list.addView(back);});}catch(Exception e){runOnUiThread(()->{list.removeAllViews();list.addView(cardText("Erreur de chargement",e.getMessage()));Button retry=button("Réessayer");retry.setOnClickListener(v->loadAdminPlayers());list.addView(retry);});}});
    }
    private void appendAdminPlayerCards(LinearLayout target,JSONArray a){
        if(a==null||a.length()==0){target.addView(cardText("Aucun joueur","Aucune donnée joueur disponible."));return;}
        for(int i=0;i<Math.min(a.length(),100);i++){
            JSONObject x=a.optJSONObject(i);if(x==null)continue;JSONObject m=x.optJSONObject("member"),p=x.optJSONObject("profile");if(p==null)p=new JSONObject();String name=p.optString("nickname",join(p.optString("firstName"),p.optString("lastName")));if(name.isEmpty()&&m!=null)name=m.optString("displayName",m.optString("email","Joueur"));String status=m==null?"":m.optString("status","");String email=m==null?"":m.optString("email","");
            LinearLayout c=card();c.addView(tv(name.isEmpty()?"Joueur":name,19,true,TEXT));c.addView(statusPill(status));if(!email.isEmpty())c.addView(infoRow("Compte",email));c.addView(infoRow("Téléphone",p.optString("phone","—")));c.addView(infoRow("Naissance",p.optString("birthDate","—")));c.addView(infoRow("Ville",p.optString("city","—")));JSONArray ms=x.optJSONArray("memberships");if(ms!=null&&ms.length()>0)c.addView(infoRow("Adhésion",prettyKey(ms.optJSONObject(0).optString("status","pending"))));Button edit=button("Modifier la fiche joueur");final JSONObject player=x;edit.setOnClickListener(v->showAdminPlayerEditor(player));c.addView(edit);target.addView(c);
        }
    }
    private void loadAdminBookings(){adminSubpage=true;content.removeAllViews();content.addView(cardText("Réservations admin","Chargement…"));loadJson("/api/confirmation",d->{content.removeAllViews();content.addView(sectionIntro("ADMINISTRATION","Réservations","Planning et confirmations issus du serveur."));JSONArray a=d.optJSONArray("bookings");if(a==null)a=d.optJSONArray("items");if(a==null||a.length()==0){content.addView(cardText("Aucune réservation","Aucune réservation disponible."));addAdminBack();return;}for(int i=0;i<Math.min(a.length(),100);i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;String ref=x.optString("reference","Réservation"),name=x.optString("name","");String status=x.optString("status","—"),confirm=x.optString("confirmationStatus","");String detail=x.optString("date")+" "+x.optString("time")+" · "+x.optInt("players",0)+" joueur(s)\nStatut : "+status+(confirm.isEmpty()?"":" · confirmation : "+confirm);content.addView(cardText(ref+(name.isEmpty()?"":" · "+name),detail));}addAdminBack();});}
    private void loadAdminLive(){
        adminSubpage=true;content.removeAllViews();
        content.addView(sectionIntro("ADMINISTRATION","Partie en direct","Créer une session, démarrer, mettre en pause, terminer et gérer les scores."));
        loadJson("/api/live-game?scope=admin",d->{
            JSONObject session=d.optJSONObject("session");
            if(session==null){renderLiveSessionCreator(d);return;}
            renderAdminLiveSession(d,session);
        });
    }

    private void renderLiveSessionCreator(JSONObject data){
        LinearLayout c=card();c.addView(tv("Créer une partie Live",22,true,TEXT));c.addView(tv("Aucune partie active. Choisis un mode puis crée la session.",13,false,MUTED));
        JSONArray modes=data.optJSONArray("modes"), bookings=data.optJSONArray("bookings");
        if(modes==null||modes.length()==0){c.addView(tv("Aucun mode de jeu disponible côté site.",13,false,ORANGE));content.addView(c);return;}
        ArrayList<String> modeLabels=new ArrayList<>(),modeIds=new ArrayList<>();for(int i=0;i<modes.length();i++){JSONObject m=modes.optJSONObject(i);if(m==null)continue;modeLabels.add(m.optString("name","Mode"));modeIds.add(m.optString("id"));}
        Spinner mode=new Spinner(this);mode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,modeLabels));c.addView(fieldWrap("Mode de jeu",mode));
        ArrayList<String> bookingLabels=new ArrayList<>(),bookingIds=new ArrayList<>();bookingLabels.add("Sans réservation liée");bookingIds.add("");if(bookings!=null)for(int i=0;i<bookings.length();i++){JSONObject b=bookings.optJSONObject(i);if(b==null)continue;bookingLabels.add(b.optString("reference","Réservation")+" · "+b.optString("name",""));bookingIds.add(b.optString("id"));}
        Spinner booking=new Spinner(this);booking.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,bookingLabels));c.addView(fieldWrap("Réservation liée",booking));
        EditText name=editField("Nom de la partie","Partie Unité Paintball");c.addView(name);
        EditText teams=editField("Nombre d’équipes","2");teams.setInputType(InputType.TYPE_CLASS_NUMBER);c.addView(teams);
        EditText duration=editField("Durée en minutes","15");duration.setInputType(InputType.TYPE_CLASS_NUMBER);c.addView(duration);
        EditText rounds=editField("Nombre de manches","1");rounds.setInputType(InputType.TYPE_CLASS_NUMBER);c.addView(rounds);
        Button create=button("Créer la partie en direct");c.addView(create);content.addView(c);
        create.setOnClickListener(v->{if(modeIds.isEmpty())return;JSONObject body=new JSONObject();try{body.put("action","create_session");body.put("modeId",modeIds.get(mode.getSelectedItemPosition()));body.put("bookingId",bookingIds.get(booking.getSelectedItemPosition()));body.put("name",name.getText().toString().trim());body.put("teamCount",Integer.parseInt(teams.getText().toString().trim()));body.put("durationMinutes",Integer.parseInt(duration.getText().toString().trim()));body.put("totalRounds",Integer.parseInt(rounds.getText().toString().trim()));}catch(Exception e){Toast.makeText(this,"Vérifie les valeurs",Toast.LENGTH_LONG).show();return;}create.setEnabled(false);io.execute(()->{try{SyncApi.post(this,"/api/live-game",store.token(),body);runOnUiThread(()->{Toast.makeText(this,"Partie Live créée",Toast.LENGTH_SHORT).show();loadAdminLive();});}catch(Exception e){runOnUiThread(()->{create.setEnabled(true);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});});
        Button back=secondaryButton("← Retour à l’administration");back.setOnClickListener(v->{section="Admin";render();});content.addView(back);
    }

    private void renderAdminLiveSession(JSONObject d,JSONObject session){
        String sessionId=session.optString("id","");String status=session.optString("status","preparation");content.addView(hero(session.optString("name","Partie en cours"),"Statut : "+status+" · Manche "+session.optInt("currentRound",1)+"/"+session.optInt("totalRounds",1)));
        LinearLayout controls=row();Button start=button("▶ Démarrer");Button pause=secondaryButton("⏸ Pause");Button finish=secondaryButton("■ Terminer");start.setEnabled(!"running".equals(status)&&!"finished".equals(status));pause.setEnabled("running".equals(status));finish.setEnabled(!"finished".equals(status));start.setOnClickListener(v->setLiveSessionStatus(sessionId,"running",session.optInt("remainingSeconds",session.optInt("durationSeconds",0))));pause.setOnClickListener(v->setLiveSessionStatus(sessionId,"paused",session.optInt("remainingSeconds",0)));finish.setOnClickListener(v->setLiveSessionStatus(sessionId,"finished",session.optInt("remainingSeconds",0)));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(50),1);cp.setMargins(dp(4),0,dp(4),dp(12));controls.addView(start,cp);controls.addView(pause,new LinearLayout.LayoutParams(cp));controls.addView(finish,new LinearLayout.LayoutParams(cp));content.addView(controls);
        JSONArray teams=d.optJSONArray("teams");if(teams!=null)for(int i=0;i<teams.length();i++){JSONObject t=teams.optJSONObject(i);if(t==null)continue;LinearLayout tc=card();tc.addView(tv(t.optString("name","Équipe"),18,true,TEXT));tc.addView(tv("Score : "+t.optInt("score",0),15,false,MUTED));LinearLayout r=row();Button minus=secondaryButton("−1");Button plus=button("+1");Button plusFive=button("+5");String teamId=t.optString("id","");int current=t.optInt("score",0);minus.setOnClickListener(v->setLiveTeamScore(teamId,current-1));plus.setOnClickListener(v->setLiveTeamScore(teamId,current+1));plusFive.setOnClickListener(v->setLiveTeamScore(teamId,current+5));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(48),1);rp.setMargins(dp(4),dp(8),dp(4),0);r.addView(minus,rp);r.addView(plus,new LinearLayout.LayoutParams(rp));r.addView(plusFive,new LinearLayout.LayoutParams(rp));tc.addView(r);content.addView(tc);}
        JSONArray players=d.optJSONArray("players");content.addView(cardText("Joueurs présents",String.valueOf(players==null?0:players.length())));Button back=secondaryButton("← Retour à l’administration");back.setOnClickListener(v->{section="Admin";render();});content.addView(back);
    }

    private void setLiveSessionStatus(String sessionId,String status,int remainingSeconds){
        if(sessionId==null||sessionId.isEmpty())return;
        io.execute(()->{try{
            SyncApi.patch(this,"/api/live-game",store.token(),new JSONObject().put("action","session_status").put("sessionId",sessionId).put("status",status).put("remainingSeconds",remainingSeconds));
            runOnUiThread(()->{Toast.makeText(this,"Partie mise à jour",Toast.LENGTH_SHORT).show();loadAdminLive();refresh(false);});
        }catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}});
    }

    private void setLiveTeamScore(String teamId,int score){
        if(teamId==null||teamId.isEmpty())return;
        io.execute(()->{try{
            SyncApi.patch(this,"/api/live-game",store.token(),new JSONObject().put("action","team_score").put("teamId",teamId).put("score",score));
            runOnUiThread(this::loadAdminLive);
        }catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}});
    }

    private interface JsonConsumer { void accept(JSONObject data); }
    private void loadJson(String path, JsonConsumer consumer){
        JSONObject cached=diskCache.getJson(path);
        if(cached!=null){consumer.accept(cached);}
        ProgressBar pb=null;if(cached==null){pb=new ProgressBar(this);content.addView(pb);}final ProgressBar loading=pb;
        io.execute(()->{try{JSONObject data=ApiClient.get(path,store.token());String before=diskCache.getJsonText(path);diskCache.putJson(path,data);String after=data.toString();runOnUiThread(()->{if(loading!=null&&loading.getParent()!=null)content.removeView(loading);if(cached==null)consumer.accept(data);else if(!after.equals(before)){/* la prochaine passe temps réel rendra l'écran si nécessaire */}});}catch(Exception e){if(cached==null)runOnUiThread(()->{if(loading!=null&&loading.getParent()!=null)content.removeView(loading);LinearLayout err=card();err.setBackground(makeBg(AppUiPrefs.isLight(this)?Color.rgb(255,241,232):Color.rgb(44,24,18),16,ORANGE_DARK));err.addView(tv("Connexion impossible",18,true,TEXT));err.addView(tv("Aucune copie locale disponible. Vérifie Internet puis réessaie.\n"+safeError(e),13,false,MUTED));Button retry=secondaryButton("Réessayer");retry.setOnClickListener(v->{content.removeView(err);loadJson(path,consumer);});err.addView(retry);content.addView(err);});}});
    }
    private String safeError(Exception e){String m=e==null?"":String.valueOf(e.getMessage());if(m.length()>160)m=m.substring(0,157)+"…";return m.replace(AppConfig.BASE_URL,"");}

    private void renderLive(){
        LinearLayout intro=new LinearLayout(this);intro.setOrientation(LinearLayout.VERTICAL);intro.setPadding(dp(6),dp(12),dp(6),dp(12));
        TextView ov=tv("UNITÉ PAINTBALL LIVE",12,true,NeonPalette.CYAN);ov.setLetterSpacing(.16f);intro.addView(ov);intro.addView(tv("Partie en cours",31,true,TEXT));
        LinearLayout video=card();video.setBackground(makeBg(CARD_2,18,ORANGE));video.addView(tv("LIVE VIDÉO",12,true,ORANGE));video.addView(tv("Choisis la source du direct : caméra du terrain ou YouTube Live. Les URL réelles restent configurées côté administration.",13,false,MUTED));LinearLayout vr=row();Button cam=secondaryButton("📷 Terrain");Button yt=secondaryButton("▶ YouTube");cam.setOnClickListener(v->Toast.makeText(this,"Caméra terrain : passerelle vidéo à configurer.",Toast.LENGTH_LONG).show());yt.setOnClickListener(v->Toast.makeText(this,"YouTube Live : URL à configurer.",Toast.LENGTH_LONG).show());vr.addView(cam,new LinearLayout.LayoutParams(0,dp(46),1));LinearLayout.LayoutParams ylp=new LinearLayout.LayoutParams(0,dp(46),1);ylp.setMargins(dp(8),0,0,0);vr.addView(yt,ylp);video.addView(vr);content.addView(video);
        LinearLayout tabs=row();TextView directTab=filterPill("Direct");TextView rankTab=filterPill("Classement");TextView historyTab=filterPill("Mes parties");directTab.setOnClickListener(v->{section="Live";render();});rankTab.setOnClickListener(v->renderLiveLeaderboard());historyTab.setOnClickListener(v->renderLiveHistory());tabs.addView(directTab,new LinearLayout.LayoutParams(0,dp(48),1));tabs.addView(rankTab,new LinearLayout.LayoutParams(0,dp(48),1));tabs.addView(historyTab,new LinearLayout.LayoutParams(0,dp(48),1));intro.addView(tabs);content.addView(intro);
        loadJson("/api/live-game?scope=live", data -> {
            JSONObject live=data.optJSONObject("session");
            if(live==null){LinearLayout empty=new LinearLayout(this);empty.setOrientation(LinearLayout.VERTICAL);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(10),dp(170),dp(10),dp(180));empty.addView(tv("🎮",48,true,NeonPalette.CYAN));TextView h=tv("Aucune partie en cours",28,true,TEXT);h.setGravity(Gravity.CENTER);h.setPadding(0,dp(22),0,0);empty.addView(h);TextView t=tv("La prochaine session apparaîtra ici dès sa création.",15,false,TEXT);t.setGravity(Gravity.CENTER);t.setPadding(0,dp(18),0,0);empty.addView(t);content.addView(empty);return;}
            LinearLayout hero=hero(live.optString("name","Partie en cours"),"Manche "+live.optInt("currentRound",1)+" / "+live.optInt("totalRounds",1));
            hero.addView(statusPill(live.optString("status","En cours")));content.addView(hero);
            JSONArray teams=data.optJSONArray("teams");if(teams!=null){LinearLayout board=card();board.addView(tv("Score",18,true,TEXT));for(int i=0;i<teams.length();i++){JSONObject t=teams.optJSONObject(i);if(t==null)continue;LinearLayout line=infoRow(t.optString("name","Équipe"),String.valueOf(t.optInt("score",0)));board.addView(line);}content.addView(board);}
            JSONArray rounds=data.optJSONArray("rounds");if(rounds!=null&&rounds.length()>0){JSONObject r=rounds.optJSONObject(0);if(r!=null)content.addView(cardText("Manche "+r.optInt("roundNumber",1),r.optString("status","")));}
            JSONArray events=data.optJSONArray("events");if(events!=null&&events.length()>0){LinearLayout c=card();c.addView(tv("Dernières actions",18,true,TEXT));for(int i=0;i<Math.min(events.length(),8);i++){JSONObject e=events.optJSONObject(i);if(e==null)continue;TextView line=tv("•  "+e.optString("label",e.optString("type","Événement")),13,false,MUTED);line.setPadding(0,dp(7),0,0);c.addView(line);}content.addView(c);}
        });
    }

        private void renderLiveLeaderboard(){
        content.removeAllViews();content.addView(sectionIntro("UNITÉ PAINTBALL LIVE","Classement","Classement réel calculé depuis les parties terminées aujourd’hui."));
        loadJson("/api/live-game?scope=live",data->{JSONArray board=data.optJSONArray("leaderboard");if(board==null||board.length()==0){content.addView(cardText("Aucun classement","Les résultats apparaîtront ici après les premières parties."));return;}for(int i=0;i<board.length();i++){JSONObject x=board.optJSONObject(i);if(x==null)continue;LinearLayout c=card();c.addView(tv((i+1)+". "+x.optString("name","Joueur"),18,true,TEXT));c.addView(infoRow("Victoires",String.valueOf(x.optInt("wins",0))));c.addView(infoRow("Parties",String.valueOf(x.optInt("games",0))));c.addView(infoRow("Objectifs",String.valueOf(x.optInt("objectives",0))));c.addView(infoRow("Score",String.valueOf(x.optInt("score",0))));content.addView(c);}Button b=secondaryButton("← Retour au direct");b.setOnClickListener(v->{section="Live";render();});content.addView(b);});
    }

    private void renderLiveHistory(){
        content.removeAllViews();content.addView(sectionIntro("UNITÉ PAINTBALL LIVE","Mes parties / historique","Historique des sessions Live terminées disponible côté site."));
        loadJson("/api/live-game?scope=live",data->{JSONArray history=data.optJSONArray("history");if(history==null||history.length()==0){content.addView(cardText("Aucune partie terminée","L’historique se remplira après la fin d’une session Live."));return;}for(int i=0;i<Math.min(history.length(),30);i++){JSONObject x=history.optJSONObject(i);if(x==null)continue;content.addView(cardText(x.optString("name","Partie"),x.optString("modeName","")+" · "+x.optString("status","")+" · "+x.optString("area","")));}Button b=secondaryButton("← Retour au direct");b.setOnClickListener(v->{section="Live";render();});content.addView(b);});
    }

    private void renderQr(){
        content.addView(sectionIntro("MES ACCÈS", "Mes QR codes", "Seuls les vrais qrToken reçus du site sont affichés. Aucun QR n’est fabriqué à partir d’un identifiant local."));
        ArrayList<JSONObject> qrEntries=new ArrayList<>();
        HashSet<String> seen=new HashSet<>();
        collectQrEntries(snapshot,qrEntries,seen,"Compte","","","",0);
        if(qrEntries.isEmpty()){
            content.addView(cardText("Aucun QR sécurisé disponible","L’application n’a reçu aucun qrToken du site pour ton compte ou tes réservations. Actualise après validation de ta réservation."));
            Button refresh=button("Actualiser les QR");refresh.setOnClickListener(v->refresh(true));content.addView(refresh);return;
        }
        for(int i=0;i<qrEntries.size();i++){
            JSONObject q=qrEntries.get(i);String token=q.optString("token","");if(token.isEmpty())continue;
            LinearLayout c=card();c.setBackground(makeBg(CARD_2,18,LIME));
            c.addView(miniPill(q.optString("type","QR sécurisé"),false));
            String label=q.optString("label","");if(!label.isEmpty()){TextView h=tv(label,20,true,TEXT);h.setPadding(0,dp(10),0,0);c.addView(h);}
            try{ImageView iv=new ImageView(this);iv.setImageBitmap(qr(token,700));iv.setAdjustViewBounds(true);iv.setBackground(makeBg(Color.rgb(245,240,232),16,Color.rgb(245,240,232)));iv.setPadding(dp(14),dp(14),dp(14),dp(14));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(310));ip.setMargins(dp(12),dp(12),dp(12),dp(12));c.addView(iv,ip);}catch(Exception ignored){}
            if(!q.optString("reference","").isEmpty())c.addView(infoRow("Réservation / référence",q.optString("reference")));
            if(!q.optString("date","").isEmpty())c.addView(infoRow("Date",q.optString("date")));
            if(!q.optString("status","").isEmpty())c.addView(infoRow("Statut",prettyKey(q.optString("status"))));
            if(!q.optString("participant","").isEmpty())c.addView(infoRow("Participant",q.optString("participant")));
            content.addView(c);
        }
    }

    private void collectQrEntries(Object node,ArrayList<JSONObject> out,HashSet<String> seen,String type,String date,String reference,String status,int depth){
        if(node==null||depth>7)return;
        try{
            if(node instanceof JSONObject){
                JSONObject o=(JSONObject)node;
                String d=firstNonEmpty(o.optString("date",""),o.optString("eventDate",""),date);
                String ref=firstNonEmpty(o.optString("reference",""),o.optString("bookingReference",""),reference);
                String st=firstNonEmpty(o.optString("status",""),status);
                String participant=firstNonEmpty(o.optString("participantName",""),o.optString("name",""),join(o.optString("firstName",""),o.optString("lastName","")));
                String token=firstNonEmpty(o.optString("qrToken",""),o.optString("qr_token",""));
                if(!token.isEmpty()&&!seen.contains(token)){
                    seen.add(token);JSONObject q=new JSONObject();q.put("token",token);q.put("type",type);q.put("date",d);q.put("reference",ref);q.put("status",st);q.put("participant",participant);q.put("label",participant.isEmpty()?type:participant);out.add(q);
                }
                java.util.Iterator<String> it=o.keys();while(it.hasNext()){String k=it.next();Object child=o.opt(k);if(child instanceof JSONObject||child instanceof JSONArray){String nextType=qrTypeFromKey(k,type);collectQrEntries(child,out,seen,nextType,d,ref,st,depth+1);}}
            }else if(node instanceof JSONArray){JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++)collectQrEntries(a.opt(i),out,seen,type,date,reference,status,depth+1);}
        }catch(Exception ignored){}
    }

    private String qrTypeFromKey(String key,String fallback){
        String k=key==null?"":key.toLowerCase(Locale.FRANCE);
        if(k.contains("participant"))return "QR participant";
        if(k.contains("booking")||k.contains("reservation"))return "QR réservation";
        if(k.contains("invite"))return "QR invitation";
        if(k.contains("tournament")||k.contains("tournoi"))return "QR tournoi";
        if(k.contains("event")||k.contains("evenement"))return "QR événement";
        if(k.contains("player")||k.contains("profile")||k.contains("joueur"))return "QR joueur";
        return fallback==null||fallback.isEmpty()?"QR sécurisé":fallback;
    }

    private String firstNonEmpty(String... values){for(String v:values)if(v!=null&&!v.trim().isEmpty())return v.trim();return "";}


    /** 2.2.31 : profil centré sur l'identité de jeu, la progression et les actions utiles. */
    private void renderProfile(){
        JSONObject p=snapshot.optJSONObject("profile"),u=snapshot.optJSONObject("user");
        if(p==null){content.addView(cardText("Profil","Profil non disponible."));return;}
        String nickname=p.optString("nickname",join(p.optString("firstName"),p.optString("lastName")));if(nickname==null||nickname.trim().isEmpty())nickname="Joueur Unité";
        content.addView(sectionIntro("PROFIL","Carte joueur","Ta progression et tes accès importants d'abord. Tes informations personnelles restent disponibles plus bas."));
        ImageView profileBanner=new ImageView(this);profileBanner.setScaleType(ImageView.ScaleType.CENTER_CROP);loadAssetImage(profileBanner,"profile_visuals/standard/banner_operation_jura.png");LinearLayout.LayoutParams bannerLp=new LinearLayout.LayoutParams(-1,dp(120));bannerLp.setMargins(0,0,0,dp(10));content.addView(profileBanner,bannerLp);

        LinearLayout operator=card();operator.setBackground(makeBg(NeonPalette.SURFACE,22,NeonPalette.CYAN));
        LinearLayout head=row();
        String avatarMode=p.optString("avatarMode","none"),avatarUrl=p.optString("avatarUrl","");
        if("photo".equals(avatarMode)&&!avatarUrl.isEmpty()){
            ImageView av=new ImageView(this);av.setScaleType(ImageView.ScaleType.CENTER_CROP);av.setBackground(makeBg(CARD_2,999,NeonPalette.CYAN));head.addView(av,new LinearLayout.LayoutParams(dp(82),dp(82)));loadRemoteImage(av,avatarUrl);
        }else if(!p.optString("avatarSvg", "").isEmpty()){android.webkit.WebView av=new android.webkit.WebView(this);av.getSettings().setJavaScriptEnabled(false);av.getSettings().setAllowFileAccess(false);av.setBackgroundColor(Color.TRANSPARENT);av.loadDataWithBaseURL(AppConfig.BASE_URL,"<html><body style='margin:0'>"+p.optString("avatarSvg")+"</body></html>","text/html","UTF-8",null);head.addView(av,new LinearLayout.LayoutParams(dp(82),dp(82)));}else{
            String avatarText="preset".equals(avatarMode)?presetIcon(p.optString("avatarPreset","ranger")):initials(join(p.optString("firstName"),p.optString("lastName")));
            TextView av=tv(avatarText,24,true,TEXT);av.setGravity(Gravity.CENTER);av.setBackground(makeBg(CARD_2,999,NeonPalette.CYAN));head.addView(av,new LinearLayout.LayoutParams(dp(82),dp(82)));
        }
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(14),dp(5),0,0);copy.addView(tv("OPÉRATEUR UNITÉ",10,true,NeonPalette.CYAN));copy.addView(tv(nickname,25,true,TEXT));String full=join(p.optString("firstName"),p.optString("lastName"));if(full!=null&&!full.trim().isEmpty()&&!full.equalsIgnoreCase(nickname))copy.addView(tv(full,12,false,MUTED));head.addView(copy,new LinearLayout.LayoutParams(0,-2,1));ImageView rankBadge=new ImageView(this);rankBadge.setScaleType(ImageView.ScaleType.CENTER_INSIDE);loadAssetImage(rankBadge,"profile_visuals/standard/badge_recrue.png");LinearLayout.LayoutParams rbp=new LinearLayout.LayoutParams(dp(54),dp(54));rbp.setMargins(dp(6),dp(10),0,0);head.addView(rankBadge,rbp);operator.addView(head);
        LinearLayout ar=row();Button avatar=secondaryButton("AVATAR");avatar.setOnClickListener(v->startActivity(new Intent(this,SoldierActivity.class)));Button photo=secondaryButton("PHOTO");photo.setOnClickListener(v->{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.addCategory(Intent.CATEGORY_OPENABLE);pick.setType("image/*");startActivityForResult(pick,REQ_AVATAR_IMAGE);});Button edit=button("MODIFIER");edit.setOnClickListener(v->showProfileEditor(p));ar.addView(avatar,new LinearLayout.LayoutParams(0,dp(44),1));LinearLayout.LayoutParams ph=new LinearLayout.LayoutParams(0,dp(44),1);ph.setMargins(dp(6),0,dp(6),0);ar.addView(photo,ph);ar.addView(edit,new LinearLayout.LayoutParams(0,dp(44),1));operator.addView(ar);content.addView(operator);

        LinearLayout progressionHost=new LinearLayout(this);progressionHost.setOrientation(LinearLayout.VERTICAL);content.addView(progressionHost);loadProfileProgression(progressionHost);

        LinearLayout shortcuts=card();shortcuts.addView(tv("MES RACCOURCIS",11,true,ORANGE));
        LinearLayout s1=row();s1.addView(uxTile("▣","Ma partie","Réservation & présence","Espace joueur",NeonPalette.CYAN),weight());s1.addView(uxTile("⌁","Mes QR","Accès terrain","QR",NeonPalette.GREEN),weight());shortcuts.addView(s1);
        LinearLayout s2=row();s2.addView(uxTile("⚙","Réglages","Compte & confidentialité","Paramètres",NeonPalette.CYAN_BRIGHT),weight());s2.addView(uxTile("?","Aide","FAQ & support","Aide",NeonPalette.CYAN),weight());shortcuts.addView(s2);content.addView(shortcuts);

        LinearLayout identity=card();identity.addView(tv("INFORMATIONS PERSONNELLES",11,true,MUTED));
        if(u!=null&&!u.optString("email","").isEmpty())identity.addView(infoRow("E-mail",u.optString("email")));
        identity.addView(infoRow("Téléphone",p.optString("phone","—")));identity.addView(infoRow("Ville",p.optString("city","—")));identity.addView(infoRow("Contact d'urgence",p.optString("emergencyContact","—")));
        Button editIdentity=secondaryButton("MODIFIER MES INFORMATIONS");editIdentity.setOnClickListener(v->showProfileEditor(p));identity.addView(editIdentity);content.addView(identity);

        JSONArray gp=p.optJSONArray("gamePreferences");StringBuilder prefText=new StringBuilder();if(gp!=null)for(int i=0;i<gp.length();i++){String x=gp.optString(i,"");if(x.isEmpty())continue;if(prefText.length()>0)prefText.append(" · ");prefText.append(x);}LinearLayout prefs=card();prefs.addView(tv("STYLE DE JEU",11,true,ORANGE));prefs.addView(tv(prefText.length()==0?"Ajoute tes préférences pour personnaliser ton profil.":prefText.toString(),14,false,TEXT));content.addView(prefs);

        LinearLayout privacy=card();privacy.addView(tv("DONNÉES & CONFIDENTIALITÉ",11,true,MUTED));privacy.addView(tv("Tes données restent liées au même compte que le site.",12,false,MUTED));Button export=secondaryButton("TÉLÉCHARGER MES DONNÉES");export.setOnClickListener(v->downloadMyData());privacy.addView(export);content.addView(privacy);

        Button logout=secondaryButton("SE DÉCONNECTER");logout.setOnClickListener(v->{FirebasePushManager.unregisterBestEffort(this);store.clearToken();CookieManager.getInstance().removeAllCookies(value -> CookieManager.getInstance().flush());auth();});content.addView(logout);
    }

    private void renderProfileLegacy(){
        JSONObject p=snapshot.optJSONObject("profile"),u=snapshot.optJSONObject("user");
        if(p==null){content.addView(cardText("Profil","Profil non disponible."));return;}
        content.addView(sectionIntro("ESPACE JOUEUR", "Mon profil & ma progression", "Identité, avatar, niveau, rang, Points Unité, badges et préférences au même endroit."));

        LinearLayout identity=card();
        identity.addView(tv("Ma fiche joueur",24,true,TEXT));
        TextView up=miniPill("À jour",false);identity.addView(up,new LinearLayout.LayoutParams(-2,-2));
        LinearLayout avatarBox=card();avatarBox.addView(tv("Mon avatar",16,true,TEXT));
        String avatarMode=p.optString("avatarMode","none");
        String avatarUrl=p.optString("avatarUrl","");
        if("photo".equals(avatarMode) && !avatarUrl.isEmpty()){
            ImageView avImg=new ImageView(this);avImg.setScaleType(ImageView.ScaleType.CENTER_CROP);avImg.setAdjustViewBounds(true);avImg.setBackground(makeBg(CARD_2,20,ORANGE));avatarBox.addView(avImg,new LinearLayout.LayoutParams(dp(105),dp(105)));loadRemoteImage(avImg,avatarUrl);
        }else{
            String avatarText="preset".equals(avatarMode)?presetIcon(p.optString("avatarPreset","ranger")):initials(join(p.optString("firstName"),p.optString("lastName")));
            TextView av=tv(avatarText,22,true,TEXT);av.setGravity(Gravity.CENTER);av.setBackground(makeBg(CARD_2,20,ORANGE));avatarBox.addView(av,new LinearLayout.LayoutParams(dp(105),dp(105)));
        }
        LinearLayout avatarActions=row();Button addPhoto=button("Ajouter une photo");Button icon=secondaryButton("Choisir une icône");
        addPhoto.setOnClickListener(v->{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.addCategory(Intent.CATEGORY_OPENABLE);pick.setType("image/*");startActivityForResult(pick,REQ_AVATAR_IMAGE);});
        icon.setOnClickListener(v->showAvatarPresetDialog(p));
        avatarActions.addView(addPhoto,new LinearLayout.LayoutParams(0,dp(48),1));LinearLayout.LayoutParams aip=new LinearLayout.LayoutParams(0,dp(48),1);aip.setMargins(dp(10),0,0,0);avatarActions.addView(icon,aip);avatarBox.addView(avatarActions);
        Button noAvatar=secondaryButton("Sans avatar");noAvatar.setOnClickListener(v->saveAvatarProfile(p,"none",p.optString("avatarPreset","ranger"),""));avatarBox.addView(noAvatar);identity.addView(avatarBox);

        identity.addView(infoRow("Prénom",p.optString("firstName")));
        identity.addView(infoRow("Nom",p.optString("lastName")));
        identity.addView(infoRow("Pseudo",p.optString("nickname")));
        identity.addView(infoRow("Téléphone",p.optString("phone")));
        identity.addView(infoRow("Date de naissance",p.optString("birthDate")));
        identity.addView(infoRow("Adresse",p.optString("address")));
        identity.addView(infoRow("Code postal",p.optString("postalCode")));
        identity.addView(infoRow("Ville",p.optString("city")));
        identity.addView(infoRow("Personne à contacter en urgence",p.optString("emergencyContact")));
        identity.addView(infoRow("Niveau",p.optString("level")));
        identity.addView(infoRow("Équipement",p.optString("equipmentMode")));
        content.addView(identity);

        LinearLayout progressionHost=new LinearLayout(this);progressionHost.setOrientation(LinearLayout.VERTICAL);content.addView(progressionHost);
        loadProfileProgression(progressionHost);

        LinearLayout actions=row();Button edit=button("Enregistrer mon profil");edit.setOnClickListener(v->showProfileEditor(p));actions.addView(edit,new LinearLayout.LayoutParams(0,dp(52),1));content.addView(actions);

        LinearLayout pref=card();pref.addView(tv("Mes préférences de jeu",18,true,TEXT));
        String[] prefs={"Stratégie","Action","Scénarios","Compétition","Loisir","Nocturne"};
        JSONArray gp=p.optJSONArray("gamePreferences");
        for(String label:prefs){CheckBox cb=new CheckBox(this);cb.setText(label);cb.setTextColor(TEXT);cb.setEnabled(false);if(gp!=null){for(int i=0;i<gp.length();i++)if(label.equalsIgnoreCase(gp.optString(i)))cb.setChecked(true);}pref.addView(cb);}
        content.addView(pref);

        LinearLayout share=card();share.addView(tv("Partager mes photos",23,true,TEXT));share.addView(tv("Choisis une image déjà enregistrée dans ta galerie ou prends une nouvelle photo.",13,false,MUTED));
        Button gallery=secondaryButton("▧ Choisir dans ma galerie");gallery.setOnClickListener(v->{section="Galerie";render();});share.addView(gallery);
        Button camera=secondaryButton("📷 Prendre une photo");camera.setOnClickListener(v->{section="Galerie";render();});share.addView(camera);content.addView(share);

        JSONArray upcoming=snapshot.optJSONArray("upcoming");
        LinearLayout bookings=card();bookings.addView(tv("Mes réservations",24,true,TEXT));
        if(upcoming==null||upcoming.length()==0)bookings.addView(tv("Aucune réservation à venir.",13,false,MUTED));
        else for(int i=0;i<Math.min(upcoming.length(),5);i++){JSONObject b=upcoming.optJSONObject(i);if(b==null)continue;LinearLayout line=card();line.addView(tv(b.optString("date")+" à "+b.optString("time"),17,true,TEXT));line.addView(tv(b.optString("reference")+" · "+b.optInt("players",1)+" joueur(s)",12,false,MUTED));line.addView(statusPill(b.optString("status","pending")));bookings.addView(line);}
        content.addView(bookings);

        LinearLayout before=card();before.addView(tv("Avant de venir",26,true,TEXT));
        JSONObject next=snapshot.optJSONObject("next");if(next!=null)before.addView(tv("Ta partie est prévue le "+next.optString("date")+" à "+next.optString("time")+". Arrive environ 15 minutes avant.",14,false,MUTED));
        before.addView(tv("À prévoir",18,true,TEXT));
        String[] checks={"Chaussures fermées","Vêtements adaptés pouvant être salis","Bouteille d’eau","Pièce d’identité si demandée","Protections personnelles si souhaité"};
        for(String x:checks){CheckBox c=new CheckBox(this);c.setText(x);c.setTextColor(TEXT);before.addView(c);}content.addView(before);

        LinearLayout dataCard=card();dataCard.addView(tv("Mes données et consentements",24,true,TEXT));dataCard.addView(tv("Télécharge tes informations ou gère tes consentements.",13,false,MUTED));Button dl=button("Télécharger mes données");dl.setOnClickListener(v->downloadMyData());dataCard.addView(dl);content.addView(dataCard);

        Button points=button("★ Ouvrir toute ma progression");points.setOnClickListener(v->{section="Points";render();});content.addView(points);
        Button qrButton=secondaryButton("Mes QR codes d’arrivée");qrButton.setOnClickListener(v->{section="QR";render();});content.addView(qrButton);
        Button logout=secondaryButton("Se déconnecter");logout.setOnClickListener(v->{FirebasePushManager.unregisterBestEffort(this);store.clearToken();CookieManager.getInstance().removeAllCookies(value -> CookieManager.getInstance().flush());auth();});content.addView(logout);
    }

    private void loadProfileProgression(LinearLayout host){
        host.removeAllViews();
        LinearLayout loading=card();loading.addView(tv("PROGRESSION UNITÉ",12,true,LIME));loading.addView(tv("Chargement de ton niveau, rang et récompenses…",13,false,MUTED));host.addView(loading);
        loadJson("/api/gamification?scope=profile", data -> {
            host.removeAllViews();
            JSONObject progression=data.optJSONObject("progression"),level=data.optJSONObject("level"),rank=data.optJSONObject("rank"),season=data.optJSONObject("season");
            if(progression==null){LinearLayout unavailable=card();unavailable.addView(tv("PROGRESSION UNITÉ",12,true,ORANGE));unavailable.addView(tv("La progression sera disponible après le déploiement serveur de la migration Gamification.",13,false,MUTED));host.addView(unavailable);return;}
            LinearLayout card=card();card.setBackground(makeBg(CARD_2,20,LIME));
            LinearLayout head=row();LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.addView(tv("PROGRESSION UNITÉ",12,true,LIME));copy.addView(tv("Niveau "+(level==null?progression.optInt("level",1):level.optInt("level",1))+" · "+(level==null?"Joueur":level.optString("label","Joueur")),22,true,TEXT));head.addView(copy,new LinearLayout.LayoutParams(0,-2,1));Button open=secondaryButton("Voir tout");open.setOnClickListener(v->{section="Points";render();});head.addView(open,new LinearLayout.LayoutParams(dp(105),dp(46)));card.addView(head);
            JSONObject selectedTitle=data.optJSONObject("selectedTitle");if(selectedTitle!=null&&!selectedTitle.optString("name","").isEmpty())card.addView(tv(selectedTitle.optString("name"),13,true,ORANGE));
            int currentXp=level==null?0:level.optInt("currentXp",0),nextXp=Math.max(1,level==null?1000:level.optInt("nextXp",1000));card.addView(tv(currentXp+" / "+nextXp+" XP",13,false,MUTED));ProgressBar xp=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);xp.setMax(nextXp);xp.setProgress(Math.min(currentXp,nextXp));LinearLayout.LayoutParams xpp=new LinearLayout.LayoutParams(-1,dp(12));xpp.setMargins(0,dp(8),0,dp(10));card.addView(xp,xpp);
            JSONArray badges=data.optJSONArray("badges"),rewards=data.optJSONArray("rewards"),tokens=data.optJSONArray("rouletteTokens"),missions=data.optJSONArray("missions");int unlockedBadges=0,availableRewards=0;if(badges!=null)for(int i=0;i<badges.length();i++){JSONObject x=badges.optJSONObject(i),link=x==null?null:x.optJSONObject("link");if(link!=null&&link.optBoolean("unlocked"))unlockedBadges++;}if(rewards!=null)for(int i=0;i<rewards.length();i++){JSONObject x=rewards.optJSONObject(i),link=x==null?null:x.optJSONObject("link");if(link!=null&&"available".equals(link.optString("status")))availableRewards++;}
            String rankLabel=rank==null?progression.optString("rankCode","Recrue"):rank.optString("name","Recrue")+" "+rank.optString("division","");String[][] vals={{"Rang",rankLabel},{"Points Unité",String.valueOf(progression.optInt("unityPoints",0))},{"Badges",String.valueOf(unlockedBadges)},{"Récompenses",String.valueOf(availableRewards)}};LinearLayout statRow=row();for(String[] x:vals){LinearLayout st=card();st.addView(tv(x[0],10,false,MUTED));st.addView(tv(x[1],16,true,TEXT));statRow.addView(st,new LinearLayout.LayoutParams(0,-2,1));}card.addView(statRow);
            if(season!=null)card.addView(infoRow("Saison",season.optString("name","Saison Unité")+" · niveau "+season.optInt("level",1)));
            if(missions!=null&&missions.length()>0){JSONObject x=missions.optJSONObject(0),m=x==null?null:x.optJSONObject("mission"),link=x==null?null:x.optJSONObject("link");if(m!=null&&link!=null)card.addView(infoRow("Mission en cours",m.optString("name","Mission")+" · "+link.optInt("progress",0)+"/"+link.optInt("target",1)));}
            JSONObject dailySpin=data.optJSONObject("dailyRoulette");boolean dailySpinReady=dailySpin!=null&&dailySpin.optBoolean("available",false);int extraSpins=0;if(tokens!=null)for(int i=0;i<tokens.length();i++){JSONObject t=tokens.optJSONObject(i);if(t!=null&&"available".equals(t.optString("status","available"))&&!"daily_free".equals(t.optString("sourceType")))extraSpins++;}
            LinearLayout shortcuts=row();Button roulette=secondaryButton(dailySpinReady?"🎯 Tour gratuit":extraSpins>0?"🎯 Roulette +"+extraSpins:"🎯 Demain"),shop=secondaryButton("🛍 Boutique"),arenaBtn=secondaryButton("⚔ Arena");roulette.setEnabled(dailySpinReady||extraSpins>0);roulette.setOnClickListener(v->renderGamificationRoulette());shop.setOnClickListener(v->renderGamificationShop());arenaBtn.setOnClickListener(v->{section="Arena";render();});shortcuts.addView(roulette,new LinearLayout.LayoutParams(0,dp(48),1));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(48),1);sp.setMargins(dp(8),0,0,0);shortcuts.addView(shop,sp);LinearLayout.LayoutParams ap2=new LinearLayout.LayoutParams(0,dp(48),1);ap2.setMargins(dp(8),0,0,0);shortcuts.addView(arenaBtn,ap2);card.addView(shortcuts);
            host.addView(card);
        });
    }

    private void downloadMyData(){
        Toast.makeText(this,"Préparation de l’export…",Toast.LENGTH_SHORT).show();
        io.execute(()->{try{
            JSONObject data=ApiClient.get("/api/privacy?export=1",store.token());
            pendingPrivacyJson=data.toString(2);
            runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"mes-donnees-unite-paintball-"+new SimpleDateFormat("yyyy-MM-dd",Locale.FRANCE).format(new Date())+".json");startActivityForResult(i,REQ_PRIVACY_EXPORT);});
        }catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Export impossible : "+e.getMessage(),Toast.LENGTH_LONG).show());}});
    }

    private void showProfileEditor(JSONObject p){
        content.removeAllViews();
        content.addView(hero("Modifier mon profil","Les changements seront synchronisés avec le site."));
        EditText firstName=editField("Prénom",p.optString("firstName"));
        EditText lastName=editField("Nom",p.optString("lastName"));
        EditText nickname=editField("Pseudo",p.optString("nickname"));
        EditText phone=editField("Téléphone",p.optString("phone")); phone.setInputType(InputType.TYPE_CLASS_PHONE);
        EditText birthDate=editField("Date de naissance",p.optString("birthDate"));
        EditText address=editField("Adresse",p.optString("address"));
        EditText postal=editField("Code postal",p.optString("postalCode"));
        EditText city=editField("Ville",p.optString("city"));
        EditText emergency=editField("Contact d'urgence",p.optString("emergencyContact"));
        Spinner level=new Spinner(this); String[] levels={"Débutant","Intermédiaire","Confirmé"}; level.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,levels));
        String currentLevel=p.optString("level","debutant"); level.setSelection("confirme".equals(currentLevel)?2:"intermediaire".equals(currentLevel)?1:0);
        Spinner equipment=new Spinner(this); String[] eq={"Location du matériel","Matériel personnel"}; equipment.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,eq)); equipment.setSelection("personnel".equals(p.optString("equipmentMode"))?1:0);
        CheckBox marketing=new CheckBox(this);marketing.setText("Recevoir les informations de l'association");marketing.setTextColor(TEXT);marketing.setChecked(p.optBoolean("marketingConsent",false));
        View[] fields={firstName,lastName,nickname,phone,birthDate,address,postal,city,emergency,level,equipment,marketing};for(View v:fields)content.addView(v);
        Button save=button("Enregistrer");content.addView(save);
        save.setOnClickListener(v->{
            JSONObject payload=new JSONObject();
            try{
                payload.put("action","save_profile");payload.put("firstName",firstName.getText().toString().trim());payload.put("lastName",lastName.getText().toString().trim());payload.put("nickname",nickname.getText().toString().trim());payload.put("phone",phone.getText().toString().trim());payload.put("birthDate",birthDate.getText().toString().trim());payload.put("address",address.getText().toString().trim());payload.put("postalCode",postal.getText().toString().trim());payload.put("city",city.getText().toString().trim());payload.put("emergencyContact",emergency.getText().toString().trim());payload.put("level",level.getSelectedItemPosition()==2?"confirme":level.getSelectedItemPosition()==1?"intermediaire":"debutant");payload.put("equipmentMode",equipment.getSelectedItemPosition()==1?"personnel":"location");payload.put("marketingConsent",marketing.isChecked());payload.put("gamePreferences",new JSONArray());payload.put("identityDocumentType",p.optString("identityDocumentType","none"));payload.put("avatarMode",p.optString("avatarMode","none"));payload.put("avatarPreset",p.optString("avatarPreset","ranger"));payload.put("avatarUrl",p.optString("avatarUrl",""));
            }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();return;}
            save.setEnabled(false);
            io.execute(()->{try{SyncApi.post(this,"/api/platform",store.token(),payload);runOnUiThread(()->{Toast.makeText(this,"Profil enregistré",Toast.LENGTH_SHORT).show();section="Profil";refresh(true);});}catch(Exception e){runOnUiThread(()->{save.setEnabled(true);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});
        });
        Button cancel=button("Annuler");cancel.setOnClickListener(v->{section="Profil";render();});content.addView(cancel);
    }


    private void updateMembershipStatus(String id,String status){
        if(id==null||id.isEmpty())return;
        io.execute(()->{try{JSONObject body=new JSONObject().put("membershipId",id).put("status",status);ApiClient.patch("/api/members",store.token(),body);runOnUiThread(()->{Toast.makeText(this,"Adhésion mise à jour",Toast.LENGTH_SHORT).show();content.removeAllViews();renderMemberships();});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}});
    }

    private void showCreateEventForm(){
        content.removeAllViews();adminSubpage=true;content.addView(sectionIntro("ADMINISTRATION","Créer un événement","La création est envoyée au site et l’événement devient visible dans le fil public."));
        EditText title=editField("Titre","");EditText date=editField("Date (AAAA-MM-JJ)",new SimpleDateFormat("yyyy-MM-dd",Locale.FRANCE).format(new Date()));EditText start=editField("Heure de début","18:00");EditText end=editField("Heure de fin","22:00");EditText capacity=editField("Capacité","40");capacity.setInputType(InputType.TYPE_CLASS_NUMBER);EditText price=editField("Prix en euros","0");price.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);EditText desc=editField("Description","");desc.setMinLines(4);content.addView(title);content.addView(date);content.addView(start);content.addView(end);content.addView(capacity);content.addView(price);content.addView(desc);
        Button save=button("Publier l’événement");content.addView(save);save.setOnClickListener(v->{if(title.getText().toString().trim().isEmpty()){Toast.makeText(this,"Titre obligatoire",Toast.LENGTH_LONG).show();return;}JSONObject b=new JSONObject();try{b.put("type","event");b.put("title",title.getText().toString().trim());b.put("eventDate",date.getText().toString().trim());b.put("startTime",start.getText().toString().trim());b.put("endTime",end.getText().toString().trim());b.put("capacity",Integer.parseInt(capacity.getText().toString().trim()));b.put("price",Double.parseDouble(price.getText().toString().trim().replace(',','.')));b.put("description",desc.getText().toString().trim());}catch(Exception e){Toast.makeText(this,"Vérifie les valeurs",Toast.LENGTH_LONG).show();return;}save.setEnabled(false);io.execute(()->{try{SyncApi.post(this,"/api/admin-content",store.token(),b);runOnUiThread(()->{Toast.makeText(this,"Événement créé sur le site",Toast.LENGTH_SHORT).show();section="Événements";render();});}catch(Exception e){runOnUiThread(()->{save.setEnabled(true);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});});
        Button back=secondaryButton("← Retour aux événements");back.setOnClickListener(v->{section="Événements";render();});content.addView(back);
    }

    private void showCreateTournamentForm(){
        content.removeAllViews();adminSubpage=true;content.addView(sectionIntro("ADMINISTRATION","Créer un tournoi","Le tournoi est créé directement dans la base du site. Il peut ensuite être ouvert aux inscriptions."));
        EditText title=editField("Nom du tournoi","");EditText date=editField("Date (AAAA-MM-JJ)",new SimpleDateFormat("yyyy-MM-dd",Locale.FRANCE).format(new Date()));EditText start=editField("Heure","09:00");EditText venue=editField("Lieu","Terrain principal");EditText deadline=editField("Fin des inscriptions (AAAA-MM-JJ)","");EditText maxTeams=editField("Nombre maximum d’équipes","8");maxTeams.setInputType(InputType.TYPE_CLASS_NUMBER);EditText perTeam=editField("Joueurs par équipe","5");perTeam.setInputType(InputType.TYPE_CLASS_NUMBER);Spinner format=spinner(new String[]{"Poules puis élimination","Championnat","Élimination directe","1v1","2v2","3v3","5v5"});Spinner mode=spinner(new String[]{"Capture du drapeau","Élimination","Domination","VIP","Bombe"});EditText rounds=editField("Nombre de manches","1");rounds.setInputType(InputType.TYPE_CLASS_NUMBER);EditText rewards=editField("Récompenses","");EditText desc=editField("Description","");desc.setMinLines(4);content.addView(title);content.addView(date);content.addView(start);content.addView(venue);content.addView(deadline);content.addView(maxTeams);content.addView(perTeam);content.addView(fieldWrap("Format",format));content.addView(fieldWrap("Mode de jeu",mode));content.addView(rounds);content.addView(rewards);content.addView(desc);
        Button save=button("Créer le tournoi");content.addView(save);save.setOnClickListener(v->{JSONObject b=new JSONObject();try{b.put("title",title.getText().toString().trim());b.put("tournamentDate",date.getText().toString().trim());b.put("startTime",start.getText().toString().trim());b.put("venue",venue.getText().toString().trim());b.put("registrationDeadline",deadline.getText().toString().trim());b.put("maximumTeams",Integer.parseInt(maxTeams.getText().toString().trim()));b.put("playersPerTeam",Integer.parseInt(perTeam.getText().toString().trim()));b.put("format",String.valueOf(format.getSelectedItem()));b.put("gameMode",String.valueOf(mode.getSelectedItem()));b.put("numberOfRounds",Integer.parseInt(rounds.getText().toString().trim()));b.put("rewards",rewards.getText().toString().trim());b.put("description",desc.getText().toString().trim());b.put("visibility","public");b.put("status","open");}catch(Exception e){Toast.makeText(this,"Vérifie les valeurs",Toast.LENGTH_LONG).show();return;}save.setEnabled(false);io.execute(()->{try{SyncApi.post(this,"/api/tournaments-admin",store.token(),b);runOnUiThread(()->{Toast.makeText(this,"Tournoi créé sur le site",Toast.LENGTH_SHORT).show();section="Tournois";render();});}catch(Exception e){runOnUiThread(()->{save.setEnabled(true);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});});
        Button back=secondaryButton("← Retour aux tournois");back.setOnClickListener(v->{section="Tournois";render();});content.addView(back);
    }

    

    private void renderArena(){
        content.addView(sectionIntro("ARENA UNITÉ","Gameplay compétitif","Affronte tes amis en duel tactique. Les choix sont résolus côté serveur et le mode classé neutralise les bonus d’équipement."));
        LinearLayout loading=card();loading.addView(tv("Chargement de l’Arena…",14,true,TEXT));content.addView(loading);
        io.execute(()->{try{JSONObject data=ApiClient.get("/api/arena",store.token());runOnUiThread(()->renderArenaLobby(data));}catch(Exception e){runOnUiThread(()->{content.removeAllViews();content.addView(sectionIntro("ARENA UNITÉ","Indisponible",e.getMessage()==null?"Vérifie que les migrations 0056 et 0057 sont appliquées.":e.getMessage()));Button web=secondaryButton("Ouvrir l’Arena dans l’application");web.setOnClickListener(v->{section="Arena";render();});content.addView(web);});}});
    }

    private void renderArenaLobby(JSONObject data){
        content.removeAllViews();content.addView(sectionIntro("ARENA UNITÉ","Quartier général","Duels tactiques, grade Arena, rivalités et accessoires débloqués par ton expérience."));
        JSONObject league=data.optJSONObject("league"),profile=data.optJSONObject("profile"),progression=data.optJSONObject("progression");
        LinearLayout hero=card();hero.setBackground(makeBg(CARD_2,18,LIME));hero.addView(tv("CARTE OPÉRATEUR · GRADE ARENA",11,true,ORANGE));hero.addView(tv((league==null?"Recrue III":league.optString("name","Recrue")+" "+league.optString("division","III")),24,true,TEXT));int arenaRating=league==null?1000:league.optInt("rating",1000);hero.addView(tv(arenaRating+" R",28,true,LIME));
        if(league!=null){int min=league.optInt("min",arenaRating),next=league.optInt("next",arenaRating);if(next>min){int max=Math.max(1,next-min),current=Math.max(0,Math.min(max,arenaRating-min));ProgressBar gradeBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);gradeBar.setMax(max);gradeBar.setProgress(current);gradeBar.setProgressTintList(android.content.res.ColorStateList.valueOf(LIME));LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,dp(9));gp.setMargins(0,dp(8),0,dp(4));hero.addView(gradeBar,gp);hero.addView(tv(Math.max(0,next-arenaRating)+" R avant promotion",11,true,MUTED));}}
        LinearLayout row=row();String[][] stats={{"Victoires",String.valueOf(profile==null?0:profile.optInt("wins",0))},{"Défaites",String.valueOf(profile==null?0:profile.optInt("losses",0))},{"Série","x"+(profile==null?0:profile.optInt("currentStreak",0))},{"Niveau",String.valueOf(progression==null?1:progression.optInt("level",1))}};for(String[] x:stats){LinearLayout c=card();c.addView(tv(x[0],10,false,MUTED));c.addView(tv(x[1],18,true,TEXT));row.addView(c,new LinearLayout.LayoutParams(0,-2,1));}hero.addView(row);content.addView(hero);

        JSONObject queue=data.optJSONObject("matchmaking");LinearLayout mm=card();mm.setBackground(makeBg(CARD_2,18,Color.rgb(32,229,243)));mm.addView(tv("MATCHMAKING",11,true,Color.rgb(32,229,243)));mm.addView(tv("Trouver un rival",22,true,TEXT));
        if(queue!=null&&"waiting".equals(queue.optString("status"))){mm.addView(tv("● Recherche en cours · "+("duel_arsenal".equals(queue.optString("mode"))?"Arsenal":"Classé")+" · "+queue.optInt("rating",1000)+" R",14,true,LIME));Button cancel=secondaryButton("Annuler la recherche");cancel.setOnClickListener(v->arenaMatchmaking(false,""));mm.addView(cancel);handler.postDelayed(()->{if("Arena".equals(section))renderArena();},4000L);}
        else if(queue!=null&&"matched".equals(queue.optString("status"))&&!queue.optString("pairedMatchId","").isEmpty()){String mid=queue.optString("pairedMatchId");mm.addView(tv("⚔ Adversaire trouvé",15,true,LIME));Button play=button("JOUER MAINTENANT");play.setOnClickListener(v->loadArenaMatch(mid));mm.addView(play);}
        else{mm.addView(tv("Recherche automatiquement un adversaire proche de ton grade. La plage s'élargit avec le temps.",12,false,MUTED));LinearLayout modes=row();Button ranked=button("◉ Classé");ranked.setOnClickListener(v->arenaMatchmaking(true,"duel_ranked"));Button arsenal=secondaryButton("⚙ Arsenal");arsenal.setOnClickListener(v->arenaMatchmaking(true,"duel_arsenal"));modes.addView(ranked,new LinearLayout.LayoutParams(0,dp(48),1));LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(0,dp(48),1);alp.setMargins(dp(8),0,0,0);modes.addView(arsenal,alp);mm.addView(modes);}content.addView(mm);

        JSONArray matches=data.optJSONArray("matches");LinearLayout pending=card();pending.addView(tv("DÉFIS & DUELS",12,true,ORANGE));boolean has=false;if(matches!=null)for(int i=0;i<matches.length();i++){JSONObject m=matches.optJSONObject(i);if(m==null)continue;String status=m.optString("status","");if(!"pending".equals(status)&&!"active".equals(status))continue;has=true;LinearLayout line=card();line.addView(tv(m.optString("opponentName","Joueur"),16,true,TEXT));line.addView(tv("active".equals(status)?"Round "+m.optInt("currentRound",1)+" · "+m.optInt("challengerScore",0)+"-"+m.optInt("opponentScore",0):("duel_ranked".equals(m.optString("mode"))?"Défi classé":"Défi Arsenal"),12,false,MUTED));String id=m.optString("id","");if("active".equals(status)){Button b=button("⚔ Reprendre le duel");b.setOnClickListener(v->loadArenaMatch(id));line.addView(b);}else if(m.optBoolean("incoming",false)){LinearLayout acts=row();Button yes=button("Accepter"),no=secondaryButton("Refuser");yes.setOnClickListener(v->arenaRespond(id,true));no.setOnClickListener(v->arenaRespond(id,false));acts.addView(yes,new LinearLayout.LayoutParams(0,dp(48),1));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,dp(48),1);np.setMargins(dp(8),0,0,0);acts.addView(no,np);line.addView(acts);}else line.addView(tv("En attente de réponse",12,true,LIME));pending.addView(line);}if(!has)pending.addView(tv("Aucun duel en cours.",13,false,MUTED));content.addView(pending);

        JSONArray friends=data.optJSONArray("friends");LinearLayout challenge=card();challenge.addView(tv("DÉFIER UN AMI",12,true,ORANGE));if(friends==null||friends.length()==0)challenge.addView(tv("Ajoute des amis pour lancer des duels.",13,false,MUTED));else for(int i=0;i<friends.length()&&i<12;i++){JSONObject f=friends.optJSONObject(i);if(f==null)continue;String uid=f.optString("userId",""),name=f.optString("name","Joueur");LinearLayout line=row();LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.addView(tv(name,15,true,TEXT));copy.addView(tv("Classé = équipement normalisé",11,false,MUTED));line.addView(copy,new LinearLayout.LayoutParams(0,-2,1));Button ranked=secondaryButton("Classé");ranked.setOnClickListener(v->arenaChallenge(uid,"duel_ranked",name));line.addView(ranked,new LinearLayout.LayoutParams(dp(92),dp(46)));Button arsenal=secondaryButton("Arsenal");LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(98),dp(46));ap.setMargins(dp(6),0,0,0);arsenal.setOnClickListener(v->arenaChallenge(uid,"duel_arsenal",name));line.addView(arsenal,ap);challenge.addView(line);}content.addView(challenge);

        JSONArray contracts=data.optJSONArray("contracts");if(contracts!=null&&contracts.length()>0){LinearLayout missions=card();missions.addView(tv("CONTRATS ARENA",12,true,ORANGE));missions.addView(tv("Objectifs de combat",22,true,TEXT));for(int i=0;i<Math.min(4,contracts.length());i++){JSONObject x=contracts.optJSONObject(i),m=x==null?null:x.optJSONObject("mission"),st=x==null?null:x.optJSONObject("state");if(m==null)continue;int target=Math.max(1,st==null?m.optInt("target",1):st.optInt("target",m.optInt("target",1))),progress=st==null?0:st.optInt("progress",0);missions.addView(infoRow(m.optString("name","Mission"),progress+" / "+target+" · +"+m.optInt("rewardXp",0)+" XP"));}content.addView(missions);}

        JSONArray items=data.optJSONArray("items");LinearLayout arsenalCard=card();arsenalCard.addView(tv("ARSENAL DÉBLOQUÉ",12,true,ORANGE));int unlocked=0,total=items==null?0:items.length();if(items!=null)for(int i=0;i<items.length();i++){JSONObject it=items.optJSONObject(i);if(it!=null&&it.optBoolean("unlocked",false))unlocked++;}arsenalCard.addView(tv(unlocked+" / "+total+" accessoires",20,true,LIME));if(items!=null)for(int i=0;i<items.length()&&i<8;i++){JSONObject it=items.optJSONObject(i);if(it==null)continue;String label=(it.optBoolean("unlocked",false)?"✓ ":"🔒 ")+it.optString("name","Accessoire")+" · "+prettyKey(it.optString("rarity","commun"))+" · niv. "+it.optInt("unlockLevel",1);arsenalCard.addView(tv(label,12,it.optBoolean("unlocked",false),it.optBoolean("unlocked",false)?TEXT:MUTED));}Button manage=button("⚙ GÉRER MON ARSENAL");manage.setOnClickListener(v->renderArenaArsenal(data));arsenalCard.addView(manage);Button web=secondaryButton("Retour au QG Arena");web.setOnClickListener(v->{section="Arena";render();});arsenalCard.addView(web);content.addView(arsenalCard);
    }

    private void renderArenaArsenal(JSONObject data){
        HashSet<String> selected=new HashSet<>();JSONArray loadouts=data.optJSONArray("loadouts");if(loadouts!=null)for(int i=0;i<loadouts.length();i++){JSONObject l=loadouts.optJSONObject(i);if(l!=null&&l.optBoolean("selected",false)){try{JSONArray ids=new JSONArray(l.optString("itemsJson","[]"));for(int j=0;j<ids.length();j++)selected.add(ids.optString(j));}catch(Exception ignored){}break;}}
        renderArenaArsenal(data,selected);
    }

    private void renderArenaArsenal(JSONObject data,HashSet<String> selected){
        content.removeAllViews();content.addView(sectionIntro("ARENA UNITÉ · ARSENAL","Équipement opérateur","Débloque des accessoires avec ton expérience. En Classé, les bonus sont neutralisés ; en Arsenal, ils donnent de petits avantages tactiques contrôlés par le serveur."));
        LinearLayout summary=card();summary.setBackground(makeBg(CARD_2,18,LIME));summary.addView(tv("LOADOUT PRINCIPAL",11,true,ORANGE));summary.addView(tv(selected.size()+" / 5 accessoire(s) équipé(s)",22,true,TEXT));summary.addView(tv("Un seul accessoire par emplacement. Aucun accessoire verrouillé ne peut être équipé.",12,false,MUTED));content.addView(summary);
        JSONArray items=data.optJSONArray("items");if(items==null||items.length()==0){content.addView(cardText("Arsenal vide","Aucun accessoire n’est encore disponible."));return;}
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);if(item==null)continue;boolean unlocked=item.optBoolean("unlocked",false),equipped=selected.contains(item.optString("id",""));String id=item.optString("id",""),slot=item.optString("slot","accessoire");
            LinearLayout c=card();c.setBackground(makeBg(CARD_2,16,equipped?LIME:STROKE));c.addView(tv((unlocked?"":"🔒 ")+item.optString("name","Accessoire"),18,true,unlocked?TEXT:MUTED));c.addView(tv(prettyKey(item.optString("rarity","commun"))+" · "+prettyKey(slot)+" · niveau "+item.optInt("unlockLevel",1),11,true,equipped?LIME:ORANGE));String desc=item.optString("description","");if(!desc.isEmpty())c.addView(tv(desc,12,false,MUTED));
            Button equip=equipped?secondaryButton("RETIRER"):button(unlocked?"ÉQUIPER":"VERROUILLÉ");equip.setEnabled(unlocked);equip.setOnClickListener(v->{HashSet<String> next=new HashSet<>(selected);if(next.contains(id))next.remove(id);else{JSONArray all=data.optJSONArray("items");if(all!=null)for(int k=0;k<all.length();k++){JSONObject other=all.optJSONObject(k);if(other!=null&&slot.equals(other.optString("slot"))){next.remove(other.optString("id"));}}if(next.size()>=5){Toast.makeText(this,"Maximum 5 accessoires.",Toast.LENGTH_SHORT).show();return;}next.add(id);}renderArenaArsenal(data,next);});c.addView(equip);content.addView(c);
        }
        Button save=button("ENREGISTRER MON ÉQUIPEMENT");save.setOnClickListener(v->saveArenaLoadout(selected));content.addView(save);Button back=secondaryButton("← Retour au QG Arena");back.setOnClickListener(v->renderArena());content.addView(back);
    }

    private void saveArenaLoadout(HashSet<String> selected){
        io.execute(()->{try{JSONObject b=new JSONObject();b.put("action","save_loadout");JSONArray ids=new JSONArray();for(String id:selected)ids.put(id);b.put("itemIds",ids);ApiClient.post("/api/arena",store.token(),b);runOnUiThread(()->{Toast.makeText(this,"Arsenal enregistré",Toast.LENGTH_SHORT).show();renderArena();});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}});
    }

    private void arenaMatchmaking(boolean join,String mode){
        io.execute(()->{try{JSONObject b=new JSONObject();b.put("action",join?"matchmaking_join":"matchmaking_leave");if(join)b.put("mode",mode);JSONObject j=ApiClient.post("/api/arena",store.token(),b);runOnUiThread(()->{if(j.optBoolean("matched",false)&&!j.optString("matchId","").isEmpty())loadArenaMatch(j.optString("matchId"));else{Toast.makeText(this,join?"Recherche Arena lancée":"Recherche annulée",Toast.LENGTH_SHORT).show();renderArena();}});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}});
    }

    private void arenaRematch(String matchId){
        io.execute(()->{try{JSONObject b=new JSONObject();b.put("action","rematch");b.put("matchId",matchId);ApiClient.post("/api/arena",store.token(),b);runOnUiThread(()->{Toast.makeText(this,"Demande de revanche envoyée",Toast.LENGTH_SHORT).show();renderArena();});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}});
    }

    private void arenaChallenge(String opponentUserId,String mode,String name){
        io.execute(()->{try{JSONObject b=new JSONObject();b.put("action","challenge");b.put("opponentUserId",opponentUserId);b.put("mode",mode);ApiClient.post("/api/arena",store.token(),b);runOnUiThread(()->{Toast.makeText(this,"Défi envoyé à "+name,Toast.LENGTH_SHORT).show();renderArena();});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}});
    }

    private void arenaRespond(String matchId,boolean accept){
        io.execute(()->{try{JSONObject b=new JSONObject();b.put("action",accept?"accept":"decline");b.put("matchId",matchId);ApiClient.post("/api/arena",store.token(),b);runOnUiThread(()->{if(accept)loadArenaMatch(matchId);else renderArena();});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}});
    }

    private void loadArenaMatch(String matchId){
        content.removeAllViews();content.addView(sectionIntro("ARENA UNITÉ","Connexion au duel","Synchronisation du round avec le serveur…"));io.execute(()->{try{JSONObject d=ApiClient.get("/api/arena?match="+java.net.URLEncoder.encode(matchId,"UTF-8"),store.token());runOnUiThread(()->renderArenaMatch(d));}catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}});
    }

    private void renderArenaMatch(JSONObject data){
        content.removeAllViews();JSONObject match=data.optJSONObject("match"),challenger=data.optJSONObject("challenger"),opponent=data.optJSONObject("opponent");if(match==null)return;String me=data.optString("me","");boolean isChallenger=me.equals(match.optString("challengerUserId",""));JSONObject mine=isChallenger?challenger:opponent,other=isChallenger?opponent:challenger;int mineScore=isChallenger?match.optInt("challengerScore",0):match.optInt("opponentScore",0),otherScore=isChallenger?match.optInt("opponentScore",0):match.optInt("challengerScore",0);content.addView(sectionIntro("ARENA UNITÉ","DUEL TACTIQUE","Les deux choix restent secrets jusqu’au verrouillage du round."));
        LinearLayout vs=card();vs.addView(tv((mine==null?"Moi":mine.optString("name","Moi"))+"  "+mineScore+"  VS  "+otherScore+"  "+(other==null?"Rival":other.optString("name","Rival")),22,true,TEXT));vs.addView(tv("Round "+match.optInt("currentRound",1)+" · "+("duel_ranked".equals(match.optString("mode"))?"CLASSÉ / équipement normalisé":"ARSENAL / bonus actifs"),12,true,LIME));content.addView(vs);
        if("finished".equals(match.optString("status"))){boolean won=me.equals(match.optString("winnerUserId",""));LinearLayout result=card();result.setBackground(makeBg(CARD_2,22,won?LIME:ORANGE));TextView resultTitle=tv(won?"🏆 VICTOIRE":"DÉFAITE",36,true,won?LIME:ORANGE);resultTitle.setGravity(Gravity.CENTER);result.addView(resultTitle);resultTitle.setScaleX(.82f);resultTitle.setScaleY(.82f);resultTitle.setAlpha(.25f);resultTitle.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(650L).start();result.addView(tv("Score final "+mineScore+" - "+otherScore,18,true,TEXT));String finishedId=match.optString("id","");Button revenge=button("⚔ Demander une revanche");revenge.setOnClickListener(v->arenaRematch(finishedId));result.addView(revenge);Button back=secondaryButton("Retour au QG Arena");back.setOnClickListener(v->renderArena());result.addView(back);content.addView(result);return;}
        JSONArray rounds=data.optJSONArray("rounds");JSONObject current=null;if(rounds!=null)for(int i=0;i<rounds.length();i++){JSONObject r=rounds.optJSONObject(i);if(r!=null&&r.optInt("roundIndex",0)==match.optInt("currentRound",1)){current=r;break;}}String locked=current==null?"":(isChallenger?current.optString("challengerAction",""):current.optString("opponentAction",""));boolean hasLocked=locked!=null&&!locked.isEmpty();
        String[][] moves={{"⚡ Assaut","assaut","Contre Objectif + Reconnaissance"},{"⬢ Couverture","couverture","Contre Assaut + Objectif"},{"↗ Contournement","contournement","Contre Couverture + Reconnaissance"},{"◉ Reconnaissance","reconnaissance","Contre Contournement + Assaut"},{"◆ Objectif","objectif","Contre Reconnaissance + Couverture"}};for(String[] mv:moves){Button b=button(mv[0]+"\n"+mv[2]);b.setEnabled(!hasLocked);String move=mv[1],id=match.optString("id","");b.setOnClickListener(v->arenaPlay(id,move));content.addView(b);}if(hasLocked){content.addView(cardText("Action verrouillée","En attente du choix du rival. Le serveur révélera les deux actions en même temps."));String liveMatchId=match.optString("id","");handler.postDelayed(()->{if("Arena".equals(section)&&!liveMatchId.isEmpty())loadArenaMatch(liveMatchId);},2200L);}
        if(rounds!=null){LinearLayout history=card();history.addView(tv("HISTORIQUE DES ROUNDS",12,true,ORANGE));for(int i=0;i<rounds.length();i++){JSONObject r=rounds.optJSONObject(i);if(r==null||r.isNull("resolvedAt"))continue;String a=isChallenger?r.optString("challengerAction",""):r.optString("opponentAction",""),b=isChallenger?r.optString("opponentAction",""):r.optString("challengerAction","");String result=r.optString("result","draw"),mineResult="draw".equals(result)?"Égalité":((isChallenger&&"challenger".equals(result))||(!isChallenger&&"opponent".equals(result))?"Gagné":"Perdu");history.addView(tv("R"+r.optInt("roundIndex",0)+" · "+a+" vs "+b+" · "+mineResult,12,true,"Gagné".equals(mineResult)?LIME:MUTED));}content.addView(history);}Button back=secondaryButton("← Quitter l’écran du duel");back.setOnClickListener(v->renderArena());content.addView(back);
    }

    private void arenaPlay(String matchId,String move){
        io.execute(()->{try{JSONObject b=new JSONObject();b.put("action","play");b.put("matchId",matchId);b.put("move",move);ApiClient.post("/api/arena",store.token(),b);runOnUiThread(()->loadArenaMatch(matchId));}catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}});
    }


    private void renderGamificationNativeHub(){
        content.removeAllViews();adminSubpage=false;
        content.addView(sectionIntro("PROGRESSION UNITÉ","Centre joueur natif","Tous les écrans ci-dessous restent dans l’application Android et utilisent la même base serveur que le site."));
        String[][] pages={{"🏆 Classements","classement"},{"🎁 Récompenses","recompenses"},{"📦 Coffres","coffres"},{"🤝 Cadeaux","cadeaux"},{"📊 Statistiques","statistiques"},{"🏷 Titres","titres"},{"✨ Cosmétiques","cosmetiques"},{"🏅 Badges","badges"},{"🎯 Missions & défis","missions"},{"🪖 Saison & Pass Unité","saison"},{"📜 Historique","historique"},{"📋 Rapport de partie","rapport"}};
        for(String[] page:pages){Button b=secondaryButton(page[0]);String key=page[1];b.setOnClickListener(v->renderGamificationNativePage(key));content.addView(b);}
        Button shop=secondaryButton("🛍 Boutique Points Unité");shop.setOnClickListener(v->renderGamificationShop());content.addView(shop);
        Button roulette=button("🎯 Roulette quotidienne");roulette.setOnClickListener(v->renderGamificationRoulette());content.addView(roulette);
        Button back=secondaryButton("← Retour à ma progression");back.setOnClickListener(v->{section="Points";render();});content.addView(back);
    }

    private void renderGamificationNativePage(String page){
        content.removeAllViews();
        final String key=page==null?"":page;
        String heading="Progression";
        if("classement".equals(key))heading="Classements";else if("recompenses".equals(key))heading="Mes récompenses";else if("coffres".equals(key))heading="Mes coffres";else if("cadeaux".equals(key))heading="Cadeaux";else if("statistiques".equals(key))heading="Statistiques";else if("titres".equals(key))heading="Titres & profil";else if("cosmetiques".equals(key))heading="Cosmétiques";else if("badges".equals(key))heading="Mes badges";else if("missions".equals(key))heading="Missions & défis";else if("saison".equals(key))heading="Saison & Pass Unité";else if("historique".equals(key))heading="Historique";else if("rapport".equals(key))heading="Rapport de partie";
        content.addView(sectionIntro("PROGRESSION UNITÉ · NATIF ANDROID",heading,"Aucun navigateur : données lues et modifiées directement via l’API sécurisée Unité Paintball."));
        if("classement".equals(key)){renderGamificationLeaderboard();return;}
        if("cadeaux".equals(key)){renderGamificationGifts();return;}
        loadJson("/api/gamification?scope=profile",data->renderGamificationNativePageData(key,data));
    }

    private void renderGamificationNativePageData(String page,JSONObject data){
        if("recompenses".equals(page)){
            JSONArray rows=data.optJSONArray("rewards");
            if(rows==null||rows.length()==0)content.addView(cardText("Aucune récompense","Tes récompenses physiques et numériques apparaîtront ici."));
            else for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i),link=x==null?null:x.optJSONObject("link"),rw=x==null?null:x.optJSONObject("reward");if(link==null||rw==null)continue;LinearLayout c=card();c.setBackground(makeBg(CARD_2,18,"available".equals(link.optString("status"))?LIME:STROKE));c.addView(tv(rw.optString("name","Récompense"),19,true,TEXT));c.addView(tv(rw.optString("description",""),12,false,MUTED));c.addView(infoRow("Statut",prettyKey(link.optString("status","available"))));String code=link.optString("validationCode","");if(!code.isEmpty())c.addView(infoRow("Code terrain",code));if(!code.isEmpty()&&"available".equals(link.optString("status"))){try{ImageView gainQr=new ImageView(this);gainQr.setImageBitmap(qr(code,700));gainQr.setBackgroundColor(Color.WHITE);gainQr.setPadding(dp(12),dp(12),dp(12),dp(12));gainQr.setContentDescription("QR de retrait : "+rw.optString("name"));c.addView(gainQr,new LinearLayout.LayoutParams(-1,dp(280)));c.addView(tv("À présenter à l’administrateur · une seule remise autorisée. Le serveur vérifie la validité au scan.",12,false,MUTED));}catch(Exception e){c.addView(tv("QR indisponible : présente le code ci-dessus.",12,false,MUTED));}}String exp=link.optString("expiresAt","");if(!exp.isEmpty())c.addView(infoRow("Expiration",exp));content.addView(c);}
        } else if("coffres".equals(page)){
            JSONArray rows=data.optJSONArray("chests");if(rows==null||rows.length()==0)content.addView(cardText("Aucun coffre","Les coffres sont gagnés par participation, saison ou événement."));else for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i),owned=x==null?null:x.optJSONObject("owned"),chest=x==null?null:x.optJSONObject("chest");if(owned==null||chest==null)continue;LinearLayout c=card();c.addView(tv(chest.optString("name","Coffre"),19,true,TEXT));c.addView(infoRow("Type",prettyKey(chest.optString("chestType","coffre"))));c.addView(infoRow("Statut",prettyKey(owned.optString("status","available"))));if("available".equals(owned.optString("status"))){Button open=button("📦 OUVRIR LE COFFRE");String id=owned.optString("id");open.setOnClickListener(v->gamificationActionToPage("open_chest",new JSONObjectSafe().putS("playerChestId",id).json(),"coffres"));c.addView(open);}content.addView(c);}
        } else if("statistiques".equals(page)){
            JSONObject st=data.optJSONObject("stats"),p=data.optJSONObject("progression");if(st==null)st=new JSONObject();java.util.Iterator<String> it=st.keys();while(it.hasNext()){String k=it.next();LinearLayout c=card();c.addView(tv(prettyKey(k),14,false,MUTED));c.addView(tv(String.valueOf(st.opt(k)),24,true,LIME));content.addView(c);}if(p!=null){content.addView(cardText("Progression compétitive","Points de rang : "+p.optInt("rankPoints",0)+"\nSérie connexion : "+p.optInt("loginStreak",0)+" jour(s)\nMeilleure série : "+p.optInt("bestLoginStreak",0)+" jour(s)"));}JSONArray mastery=data.optJSONArray("scenarioMastery");if(mastery!=null&&mastery.length()>0){LinearLayout h=card();h.addView(tv("MAÎTRISE DES SCÉNARIOS",12,true,ORANGE));for(int i=0;i<mastery.length();i++){JSONObject x=mastery.optJSONObject(i);if(x!=null)h.addView(infoRow(x.optString("scenarioCode","Scénario"),"Niv. "+x.optInt("level",1)+" · "+x.optInt("xp",0)+" XP · "+x.optInt("wins",0)+" victoire(s)"));}content.addView(h);}
        } else if("titres".equals(page)){
            JSONArray rows=data.optJSONArray("titles");JSONObject selected=data.optJSONObject("selectedTitle");if(rows==null||rows.length()==0)content.addView(cardText("Aucun titre","Les titres se débloquent avec ta progression."));else for(int i=0;i<rows.length();i++){JSONObject t=rows.optJSONObject(i);if(t==null)continue;LinearLayout c=card();boolean active=selected!=null&&t.optString("id").equals(selected.optString("id"));c.setBackground(makeBg(CARD_2,18,active?LIME:STROKE));c.addView(tv(t.optString("name","Titre"),19,true,TEXT));c.addView(tv(active?"AFFICHÉ SOUS TON PSEUDO":"DÉBLOQUÉ",11,true,active?LIME:MUTED));if(!active){Button b=secondaryButton("Afficher ce titre");String id=t.optString("id");b.setOnClickListener(v->gamificationActionToPage("select_title",new JSONObjectSafe().putS("titleId",id).json(),"titres"));c.addView(b);}content.addView(c);}
        } else if("cosmetiques".equals(page)){
            JSONArray rows=data.optJSONArray("cosmetics");if(rows==null||rows.length()==0)content.addView(cardText("Aucun cosmétique","Cadres, bannières et éléments de profil débloqués apparaîtront ici."));else for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);if(x==null)continue;LinearLayout c=card();c.addView(tv(x.optString("name","Cosmétique"),18,true,TEXT));c.addView(infoRow("Type",prettyKey(x.optString("cosmeticType","objet"))));content.addView(c);}
        } else if("badges".equals(page)){
            Badge4KPack badge4k=new Badge4KPack(this);
            LinearLayout packCard=card();packCard.setBackground(makeBg(CARD_2,20,ORANGE));packCard.addView(tv("PACK BADGES 4K",12,true,ORANGE));packCard.addView(tv(badge4k.installed()?"✓ Qualité 4K installée":"Qualité 4K optionnelle",20,true,TEXT));packCard.addView(tv("24 badges existants · mêmes identifiants et mêmes déblocages · seuls les visuels passent en 4096 px.",12,false,MUTED));
            Button packButton=button(badge4k.installed()?"SUPPRIMER LE PACK 4K":"TÉLÉCHARGER ET INSTALLER LE PACK 4K");packButton.setOnClickListener(v->{packButton.setEnabled(false);packButton.setText(badge4k.installed()?"SUPPRESSION…":"TÉLÉCHARGEMENT…");io.execute(()->{try{if(badge4k.installed())badge4k.remove();else badge4k.install((state,done,total)->runOnUiThread(()->packButton.setText(AppUiPrefs.t(this,state)+(state.equals("Téléchargement")?" · "+(done*100/total)+" %":""))));runOnUiThread(()->renderGamificationNativePage("badges"));}catch(Exception e){runOnUiThread(()->{packButton.setEnabled(true);packButton.setText("RÉESSAYER");Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});});packCard.addView(packButton);content.addView(packCard);
            JSONArray rows=data.optJSONArray("badges");if(rows==null||rows.length()==0)content.addView(cardText("Aucun badge","Ta collection se remplira avec les actions validées sur le terrain."));else for(int i=0;i<rows.length();i++){
                JSONObject x=rows.optJSONObject(i),b=x==null?null:x.optJSONObject("badge"),link=x==null?null:x.optJSONObject("link");if(b==null||link==null)continue;
                boolean unlocked=link.optBoolean("unlocked",false);String rarity=b.optString("rarity","commun");
                LinearLayout c=card();c.setPadding(dp(14),dp(14),dp(14),dp(14));c.setBackground(makeBg(CARD_2,20,unlocked?rarityColor(rarity):STROKE));
                LinearLayout head=row();head.setGravity(Gravity.CENTER_VERTICAL);
                ImageView insignia=badgeInsignia(b,unlocked);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(92),dp(92));ip.setMargins(0,0,dp(14),0);head.addView(insignia,ip);
                LinearLayout meta=new LinearLayout(this);meta.setOrientation(LinearLayout.VERTICAL);meta.addView(tv(b.optString("name","Badge"),20,true,unlocked?TEXT:MUTED));meta.addView(tv(unlocked?"✓ OBTENU":"🔒 À DÉBLOQUER",11,true,unlocked?LIME:MUTED));meta.addView(tv("◆ "+prettyKey(rarity),12,true,rarityColor(rarity)));head.addView(meta,new LinearLayout.LayoutParams(0,-2,1));c.addView(head);
                int progress=link.optInt("progress",0),target=Math.max(1,link.optInt("progressTarget",1));ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(target);bar.setProgress(Math.min(progress,target));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(unlocked?rarityColor(rarity):ORANGE));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(36,45,52)));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(9));bp.setMargins(0,dp(12),0,dp(6));c.addView(bar,bp);
                c.addView(infoRow("Progression",progress+" / "+target));String d=b.optString("description","");if(!d.isEmpty())c.addView(tv(d,12,false,MUTED));content.addView(c);
            }
        } else if("missions".equals(page)){
            JSONArray rows=data.optJSONArray("missions");if(rows==null||rows.length()==0)content.addView(cardText("Aucune mission","Les missions se synchronisent automatiquement avec tes actions réelles."));else for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i),m=x==null?null:x.optJSONObject("mission"),link=x==null?null:x.optJSONObject("link");if(m==null||link==null)continue;LinearLayout c=card();c.addView(tv(m.optString("name","Mission"),18,true,TEXT));c.addView(infoRow("Progression",link.optInt("progress",0)+" / "+Math.max(1,link.optInt("target",1))));c.addView(infoRow("Statut",prettyKey(link.optString("status","active"))));c.addView(infoRow("Récompense","+"+m.optInt("rewardXp",0)+" XP · +"+m.optInt("rewardPoints",0)+" Points"));String d=m.optString("description","");if(!d.isEmpty())c.addView(tv(d,12,false,MUTED));content.addView(c);}
            JSONArray ach=data.optJSONArray("achievements");if(ach!=null&&ach.length()>0){LinearLayout a=card();a.addView(tv("SUCCÈS PERMANENTS",12,true,ORANGE));for(int i=0;i<ach.length();i++){JSONObject x=ach.optJSONObject(i),v=x==null?null:x.optJSONObject("achievement"),l=x==null?null:x.optJSONObject("link");if(v!=null&&l!=null)a.addView(infoRow(v.optString("name","Succès"),l.optInt("progress",0)+" / "+Math.max(1,l.optInt("target",1))));}content.addView(a);}
        } else if("saison".equals(page)){
            JSONObject season=data.optJSONObject("season");if(season==null)content.addView(cardText("Aucune saison active","La prochaine saison apparaîtra automatiquement ici."));else{LinearLayout c=card();c.setBackground(makeBg(CARD_2,20,LIME));c.addView(tv(season.optString("name","Saison Unité"),24,true,TEXT));c.addView(infoRow("Niveau",season.optInt("level",1)+" / "+season.optInt("passLevels",50)));c.addView(infoRow("XP saison",season.optInt("currentXp",0)+" / "+season.optInt("nextXp",1500)));ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(Math.max(1,season.optInt("nextXp",1500)));bar.setProgress(Math.min(season.optInt("currentXp",0),bar.getMax()));c.addView(bar,new LinearLayout.LayoutParams(-1,dp(12)));content.addView(c);JSONArray pass=season.optJSONArray("passRewards");java.util.HashSet<Integer> claimed=seasonClaimedLevels(season.optJSONObject("progression"));if(pass!=null){for(int i=0;i<pass.length();i++){JSONObject r=pass.optJSONObject(i);if(r==null)continue;int lv=r.optInt("level",i+1);LinearLayout p=card();p.addView(tv("PASS UNITÉ · NIVEAU "+lv,14,true,lv<=season.optInt("level",1)?LIME:MUTED));p.addView(infoRow("Récompense",String.valueOf(r.opt("value"))+" "+prettyKey(r.optString("type","gain"))));if(lv<=season.optInt("level",1)&&!claimed.contains(lv)){Button claim=button("RÉCUPÉRER");JSONObject ex=new JSONObjectSafe().putS("level",lv).json();claim.setOnClickListener(v->gamificationActionToPage("claim_season_reward",ex,"saison"));p.addView(claim);}else if(claimed.contains(lv))p.addView(tv("✓ RÉCUPÉRÉ",11,true,LIME));content.addView(p);}}}
        } else if("historique".equals(page)){
            JSONArray tx=data.optJSONArray("transactions"),xp=data.optJSONArray("xpHistory"),gifts=data.optJSONArray("gifts");LinearLayout c=card();c.addView(tv("DERNIERS MOUVEMENTS",12,true,ORANGE));int shown=0;if(tx!=null)for(int i=0;i<tx.length()&&i<30;i++){JSONObject x=tx.optJSONObject(i);if(x==null)continue;c.addView(infoRow(prettyKey(x.optString("transactionType","Points")),(x.optInt("amount",0)>=0?"+":"")+x.optInt("amount",0)+" Points"));shown++;}if(shown==0)c.addView(tv("Aucun mouvement de Points Unité.",12,false,MUTED));content.addView(c);LinearLayout e=card();e.addView(tv("XP RÉCENTE",12,true,ORANGE));if(xp!=null)for(int i=0;i<xp.length()&&i<20;i++){JSONObject x=xp.optJSONObject(i);if(x!=null)e.addView(infoRow(x.optString("reason",prettyKey(x.optString("sourceType","XP"))),"+"+x.optInt("amount",x.optInt("xp",0))+" XP"));}content.addView(e);if(gifts!=null&&gifts.length()>0){LinearLayout g=card();g.addView(tv("CADEAUX REÇUS",12,true,ORANGE));for(int i=0;i<gifts.length()&&i<20;i++){JSONObject x=gifts.optJSONObject(i);if(x!=null)g.addView(infoRow(prettyKey(x.optString("giftType","cadeau")),String.valueOf(x.opt("value"))));}content.addView(g);}
        } else if("rapport".equals(page)){
            JSONObject r=data.optJSONObject("lastGameReport");if(r==null)content.addView(cardText("Aucun rapport récent","Le rapport apparaîtra après une partie Live Game terminée et validée."));else{LinearLayout c=card();boolean win=r.optBoolean("victory",false);c.setBackground(makeBg(CARD_2,20,win?LIME:ORANGE));c.addView(tv(win?"🏆 VICTOIRE":"PARTIE TERMINÉE",26,true,win?LIME:ORANGE));c.addView(tv(r.optString("name","Rapport de partie"),20,true,TEXT));c.addView(infoRow("Scénario",r.optString("scenario","—")));c.addView(infoRow("XP","+"+r.optInt("xpGained",0)));c.addView(infoRow("Points Unité","+"+r.optInt("pointsGained",0)));c.addView(infoRow("Points de rang","+"+r.optInt("rankPointsGained",0)));c.addView(infoRow("Objectifs",String.valueOf(r.optInt("objectives",0))));content.addView(c);}
        }
        addNativeProgressionBack();
    }

    private void renderGamificationLeaderboard(){
        LinearLayout controls=card();controls.addView(tv("FILTRES",12,true,ORANGE));Spinner period=spinner(new String[]{"Cette semaine","Ce mois","Cette saison","Tous les temps"});Spinner category=spinner(new String[]{"XP","Participations","Victoires","Objectifs","Tournois","Points Unité","Fair-play","Parrainages","Rang","Progression saisonnière"});period.setSelection(3);controls.addView(fieldWrap("Période",period));controls.addView(fieldWrap("Catégorie",category));Button go=button("ACTUALISER LE CLASSEMENT");controls.addView(go);content.addView(controls);LinearLayout results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);content.addView(results);Runnable reload=()->{String[] periods={"week","month","season","all"},cats={"xp","participations","victoires","objectifs","tournois","points","fairplay","parrainages","rank","season"};String url="/api/gamification?scope=leaderboard&period="+periods[period.getSelectedItemPosition()]+"&category="+cats[category.getSelectedItemPosition()];loadJson(url,data->{results.removeAllViews();JSONArray players=data.optJSONArray("players");LinearLayout p=card();p.addView(tv("JOUEURS",12,true,ORANGE));if(players==null||players.length()==0)p.addView(tv("Aucun classement disponible.",12,false,MUTED));else for(int i=0;i<Math.min(50,players.length());i++){JSONObject x=players.optJSONObject(i);if(x!=null)p.addView(infoRow("#"+x.optInt("rank",i+1)+" · "+x.optString("name","Joueur"),String.valueOf(x.optInt("score",0))));}results.addView(p);JSONArray teams=data.optJSONArray("teams");if(teams!=null&&teams.length()>0){LinearLayout t=card();t.addView(tv("ÉQUIPES",12,true,ORANGE));for(int i=0;i<Math.min(20,teams.length());i++){JSONObject x=teams.optJSONObject(i);if(x!=null)t.addView(infoRow("#"+x.optInt("rank",i+1)+" · "+x.optString("name","Équipe"),String.valueOf(x.optInt("score",0))));}results.addView(t);}addNativeProgressionBackTo(results);});};go.setOnClickListener(v->reload.run());reload.run();
    }

    private void renderGamificationGifts(){
        loadJson("/api/gamification?scope=profile",profile->{JSONArray received=profile.optJSONArray("gifts");if(received!=null&&received.length()>0){LinearLayout rec=card();rec.addView(tv("CADEAUX REÇUS",12,true,ORANGE));for(int i=0;i<Math.min(20,received.length());i++){JSONObject g=received.optJSONObject(i);if(g!=null)rec.addView(infoRow(prettyKey(g.optString("giftType","cadeau")),g.optInt("value",0)>0?String.valueOf(g.optInt("value",0)):g.optString("referenceId","cadeau")));}content.addView(rec);}loadJson("/api/gamification?scope=gift_contacts",contactsData->{JSONArray contacts=contactsData.optJSONArray("contacts");loadJson("/api/gamification?scope=gift_inventory",inventory->{renderNativeGiftForm(contacts,inventory);addNativeProgressionBack();});});});
    }

    private void renderNativeGiftForm(JSONArray contacts,JSONObject inventory){
        LinearLayout c=card();c.addView(tv("ENVOYER UN CADEAU",12,true,ORANGE));if(contacts==null||contacts.length()==0){c.addView(tv("Ajoute d’abord un ami pour lui envoyer un cadeau.",13,false,MUTED));content.addView(c);return;}
        String[] names=new String[contacts.length()];for(int i=0;i<contacts.length();i++){JSONObject x=contacts.optJSONObject(i);names[i]=x==null?"Joueur":x.optString("name","Joueur");}
        Spinner who=spinner(names);Spinner type=spinner(new String[]{"Points Unité","Jeton roulette","Récompense","Badge cadeau"});EditText value=editField("Nombre de Points Unité","50");value.setInputType(InputType.TYPE_CLASS_NUMBER);Spinner reference=spinner(new String[]{"—"});LinearLayout refWrap=fieldWrap("Objet à offrir",reference);c.addView(fieldWrap("Ami",who));c.addView(fieldWrap("Type",type));c.addView(value);c.addView(refWrap);
        Runnable updateRefs=()->{int pos=type.getSelectedItemPosition();java.util.ArrayList<String> labels=new java.util.ArrayList<>();java.util.ArrayList<String> ids=new java.util.ArrayList<>();if(pos==2){JSONArray rows=inventory.optJSONArray("rewards");if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i),o=x==null?null:x.optJSONObject("owned"),r=x==null?null:x.optJSONObject("reward");if(o!=null&&r!=null){ids.add(o.optString("id"));labels.add(r.optString("name","Récompense"));}}}else if(pos==3){JSONArray rows=inventory.optJSONArray("badges");if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i),b=x==null?null:x.optJSONObject("badge");if(b!=null){ids.add(b.optString("id"));labels.add(b.optString("name","Badge"));}}}reference.setTag(ids);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels.isEmpty()?new String[]{"Aucun objet disponible"}:labels.toArray(new String[0]));reference.setAdapter(a);value.setVisibility(pos==0?View.VISIBLE:View.GONE);refWrap.setVisibility(pos>=2?View.VISIBLE:View.GONE);};type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){updateRefs.run();}public void onNothingSelected(android.widget.AdapterView<?> parent){}});updateRefs.run();Button send=button("ENVOYER LE CADEAU");send.setOnClickListener(v->{JSONObject contact=contacts.optJSONObject(who.getSelectedItemPosition());if(contact==null)return;int pos=type.getSelectedItemPosition();String giftType=pos==0?"points":pos==1?"roulette_token":pos==2?"reward":"badge";JSONObject extra=new JSONObjectSafe().putS("recipientUserId",contact.optString("userId")).putS("giftType",giftType).putS("message","Cadeau Unité Paintball").json();try{extra.put("value",pos==0?Math.max(1,Integer.parseInt(value.getText().toString().trim())):1);if(pos>=2){Object tag=reference.getTag();if(tag instanceof java.util.ArrayList){java.util.ArrayList<?> ids=(java.util.ArrayList<?>)tag;int rp=reference.getSelectedItemPosition();if(rp>=0&&rp<ids.size())extra.put("referenceId",String.valueOf(ids.get(rp)));}}}catch(Exception e){Toast.makeText(this,"Valeur invalide",Toast.LENGTH_LONG).show();return;}gamificationActionToPage("send_gift",extra,"cadeaux");});c.addView(send);content.addView(c);
    }

    private boolean rewardActionBusy=false;
    private void gamificationActionToPage(String action,JSONObject extra,String page){
        if(rewardActionBusy)return;rewardActionBusy=true;
        io.execute(()->{try{JSONObject payload=extra==null?new JSONObject():extra;payload.put("action",action);JSONObject response=SyncApi.post(this,"/api/gamification",store.token(),payload);JSONObject result=response.optJSONObject("result");if("open_chest".equals(action)&&result==null)throw new Exception("Résultat indisponible : consulte ton historique avant de réessayer.");runOnUiThread(()->{rewardActionBusy=false;if("open_chest".equals(action))RewardRevealDialog.show(this,false,result.optString("rewardLabel","Récompense obtenue"),()->renderGamificationNativePage(page));else{Toast.makeText(this,"Action enregistrée",Toast.LENGTH_SHORT).show();renderGamificationNativePage(page);}});}catch(Exception e){runOnUiThread(()->{rewardActionBusy=false;Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});
    }
    private void showNotificationDiagnostics(){
        boolean allowed=NativeNotifications.canShow(this);
        android.app.job.JobScheduler scheduler=(android.app.job.JobScheduler)getSystemService(JOB_SCHEDULER_SERVICE);
        boolean scheduled=scheduler!=null&&scheduler.getAllPendingJobs().size()>0;
        String message="Autorisation Android : "+(allowed?"active":"bloquée")+"\nSynchronisation en arrière-plan : "+(scheduled?"planifiée":"non planifiée")+"\nPush Firebase : "+(FirebasePushManager.isConfigured(this)?"configuré":"non configuré")+"\n\nSans Firebase, vérification périodique à partir de 15 minutes, selon Android. Le test ci-dessous est local : il ne confirme pas la réception depuis le serveur.";
        new AlertDialog.Builder(this).setTitle("Notifications Unité Paintball").setMessage(message).setPositiveButton(allowed?"Envoyer le test local":"Réglages Android",(d,w)->{if(allowed)NativeNotifications.show(this,90210,"Test local Unité Paintball","Si cette alerte apparaît, Android autorise les notifications de l’application.","general","/notifications",false);else startActivity(new android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()));}).setNegativeButton("Fermer",null).show();
    }

    private java.util.HashSet<Integer> seasonClaimedLevels(JSONObject progression){
        java.util.HashSet<Integer> out=new java.util.HashSet<>();if(progression==null)return out;Object raw=progression.opt("rewardsClaimed");try{JSONArray a=raw instanceof JSONArray?(JSONArray)raw:new JSONArray(String.valueOf(raw==null?"[]":raw));for(int i=0;i<a.length();i++)out.add(a.optInt(i));}catch(Exception ignored){}return out;
    }

    private int rarityColor(String rarity){String r=rarity==null?"":rarity.toLowerCase(Locale.ROOT);if(r.contains("myth"))return NeonPalette.MYTHIC;if(r.contains("légend")||r.contains("legend"))return NeonPalette.LEGENDARY;if(r.contains("épique")||r.contains("epic"))return NeonPalette.EPIC;if(r.contains("rare"))return NeonPalette.RARE;if(r.contains("peu")||r.contains("uncommon"))return NeonPalette.UNCOMMON;return NeonPalette.COMMON;}

    private void addNativeProgressionBack(){Button b=secondaryButton("← Centre Progression");b.setOnClickListener(v->renderGamificationNativeHub());content.addView(b);}
    private void addNativeProgressionBackTo(LinearLayout host){Button b=secondaryButton("← Centre Progression");b.setOnClickListener(v->renderGamificationNativeHub());host.addView(b);}

    private String progressionTabFromPath(String path){if(path==null)return "";int i=path.indexOf("tab=");if(i<0)return "";String v=path.substring(i+4);int amp=v.indexOf('&');if(amp>=0)v=v.substring(0,amp);if("classement".equals(v)||"recompenses".equals(v)||"coffres".equals(v)||"cadeaux".equals(v)||"statistiques".equals(v)||"titres".equals(v)||"badges".equals(v)||"missions".equals(v)||"saison".equals(v)||"cosmetiques".equals(v)||"historique".equals(v)||"rapport".equals(v))return v;if("boutique".equals(v))return "boutique";if("roulette".equals(v))return "roulette";return "";}

    private void openSitePath(String path){
        if(path==null||path.trim().isEmpty())return;
        String localPath=path;
        if(path.startsWith(AppConfig.BASE_URL))localPath=path.substring(AppConfig.BASE_URL.length());
        if(localPath.startsWith("/progression")){
            String tab=progressionTabFromPath(localPath);
            if("boutique".equals(tab)){renderGamificationShop();return;}
            if("roulette".equals(tab)){renderGamificationRoulette();return;}
            if(tab.isEmpty())renderGamificationNativeHub();else renderGamificationNativePage(tab);
            return;
        }
        if(localPath.startsWith("/arena")){section="Arena";render();return;}
        String url=path.startsWith("http")?path:AppConfig.BASE_URL+(path.startsWith("/")?path:"/"+path);try{startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));}catch(Exception e){Toast.makeText(this,"Impossible d’ouvrir le document",Toast.LENGTH_LONG).show();}
    }

    

    

    private void showAdminPlayerEditor(JSONObject player){
        JSONObject m=player.optJSONObject("member"),p=player.optJSONObject("profile");if(p==null)p=new JSONObject();final JSONObject profile=p;final String userId=m==null?"":m.optString("userId","");if(userId.isEmpty()){Toast.makeText(this,"Identifiant joueur indisponible",Toast.LENGTH_LONG).show();return;}content.removeAllViews();adminSubpage=true;content.addView(sectionIntro("ADMINISTRATION","Modifier la fiche joueur","Les changements sont enregistrés sur le site et journalisés."));
        EditText first=editField("Prénom",p.optString("firstName",""));EditText last=editField("Nom",p.optString("lastName",""));EditText nick=editField("Pseudo",p.optString("nickname",""));EditText phone=editField("Téléphone",p.optString("phone",""));EditText birth=editField("Date de naissance",p.optString("birthDate",""));EditText address=editField("Adresse",p.optString("address",""));EditText postal=editField("Code postal",p.optString("postalCode",""));EditText city=editField("Ville",p.optString("city",""));EditText emergency=editField("Contact d’urgence",p.optString("emergencyContact",""));EditText guardian=editField("Responsable légal",p.optString("guardianName",""));EditText guardianRel=editField("Lien avec le mineur",p.optString("guardianRelationship",""));EditText guardianPhone=editField("Téléphone responsable légal",p.optString("guardianPhone",""));Spinner level=spinner(new String[]{"debutant","intermediaire","confirme"});String lv=p.optString("level","debutant");level.setSelection("confirme".equals(lv)?2:"intermediaire".equals(lv)?1:0);Spinner equip=spinner(new String[]{"location","personnel"});equip.setSelection("personnel".equals(p.optString("equipmentMode"))?1:0);View[] vs={first,last,nick,phone,birth,address,postal,city,emergency,guardian,guardianRel,guardianPhone};for(View v:vs)content.addView(v);content.addView(fieldWrap("Niveau",level));content.addView(fieldWrap("Équipement",equip));
        Button save=button("Enregistrer les modifications");content.addView(save);save.setOnClickListener(v->{JSONObject body=new JSONObject();try{body.put("action","update_profile");body.put("userId",userId);body.put("firstName",first.getText().toString().trim());body.put("lastName",last.getText().toString().trim());body.put("nickname",nick.getText().toString().trim());body.put("phone",phone.getText().toString().trim());body.put("birthDate",birth.getText().toString().trim());body.put("address",address.getText().toString().trim());body.put("postalCode",postal.getText().toString().trim());body.put("city",city.getText().toString().trim());body.put("emergencyContact",emergency.getText().toString().trim());body.put("guardianName",guardian.getText().toString().trim());body.put("guardianRelationship",guardianRel.getText().toString().trim());body.put("guardianPhone",guardianPhone.getText().toString().trim());body.put("level",String.valueOf(level.getSelectedItem()));body.put("equipmentMode",String.valueOf(equip.getSelectedItem()));}catch(Exception ignored){}save.setEnabled(false);io.execute(()->{try{SyncApi.post(this,"/api/admin-players",store.token(),body);runOnUiThread(()->{Toast.makeText(this,"Fiche joueur modifiée",Toast.LENGTH_SHORT).show();loadAdminPlayers();});}catch(Exception e){runOnUiThread(()->{save.setEnabled(true);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});});Button back=secondaryButton("← Retour aux joueurs");back.setOnClickListener(v->loadAdminPlayers());content.addView(back);
    }

    private ImageView badgeInsignia(JSONObject badge,boolean unlocked){
        ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);iv.setPadding(dp(4),dp(4),dp(4),dp(4));iv.setBackground(makeBg(Color.rgb(8,13,18),18,unlocked?rarityColor(badge.optString("rarity","commun")):STROKE));
        String asset=badgeAssetName(badge);Badge4KPack p4k=new Badge4KPack(this);try(java.io.InputStream in=p4k.installed()?p4k.openBadge(asset):getAssets().open("insignia_png/"+asset+".png")){android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();opts.inSampleSize=p4k.installed()?8:1;Bitmap bmp=android.graphics.BitmapFactory.decodeStream(in,null,opts);iv.setImageBitmap(bmp);if(!unlocked){iv.setAlpha(.38f);iv.setColorFilter(Color.rgb(130,138,145));}}catch(Exception ignored){try(java.io.InputStream in=getAssets().open("insignia_png/"+asset+".png")){iv.setImageBitmap(android.graphics.BitmapFactory.decodeStream(in));}catch(Exception e){iv.setImageResource(R.drawable.unite_paintball_logo);}iv.setAlpha(unlocked?1f:.4f);}return iv;
    }
    private String badgeAssetName(JSONObject badge){
        String image=badge.optString("imageUrl","");if(image.matches("/gamification-assets/insignia/insignia-[a-z0-9-]+\\.svg"))return image.substring(image.lastIndexOf('/')+1,image.length()-4);
        String raw=(badge.optString("code","")+" "+badge.optString("slug","")+" "+badge.optString("name","")).toLowerCase(Locale.ROOT);
        raw=java.text.Normalizer.normalize(raw,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","");
        if(raw.contains("confirm"))return "insignia-confirmed";if(raw.contains("expert"))return "insignia-expert";if(raw.contains("elite"))return "insignia-elite";if(raw.contains("recru"))return "insignia-recruit";
        if(raw.contains("halloween"))return "insignia-halloween";if(raw.contains("hiver")||raw.contains("winter"))return "insignia-winter";if(raw.contains("tournoi")||raw.contains("tournament"))return "insignia-tournament";if(raw.contains("benevol")||raw.contains("volunteer"))return "insignia-volunteer";if(raw.contains("communaut"))return "insignia-community";if(raw.contains("evenement")||raw.contains("event"))return "insignia-event";
        if(raw.contains("1000"))return "insignia-wins-1000";if(raw.contains("500"))return "insignia-wins-500";if(raw.contains("100"))return "insignia-wins-100";if(raw.contains("50"))return "insignia-wins-50";if(raw.contains("10 victoire")||raw.contains("wins-10"))return "insignia-wins-10";if(raw.contains("serie 10")||raw.contains("streak10"))return "insignia-streak10";if(raw.contains("serie 5")||raw.contains("streak5"))return "insignia-streak5";
        if(raw.contains("crimson"))return "insignia-crimson";if(raw.contains("shadow"))return "insignia-shadow";if(raw.contains("glace")||raw.contains("ice"))return "insignia-ice";if(raw.contains("feu")||raw.contains("fire"))return "insignia-fire";if(raw.contains("or")||raw.contains("gold"))return "insignia-gold";if(raw.contains("legend"))return "insignia-legend";if(raw.contains("premier")||raw.contains("first"))return "insignia-first";return "insignia-recruit";
    }

    private Spinner spinner(String[] labels){Spinner s=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels);s.setAdapter(a);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));lp.setMargins(0,dp(6),0,dp(6));s.setLayoutParams(lp);return s;}
    private String[] numberLabels(int min,int max,String singular,String plural){String[] a=new String[max-min+1];for(int i=min;i<=max;i++)a[i-min]=i+(i==1?singular:plural);return a;}
    private CheckBox acceptBox(String label){CheckBox c=new CheckBox(this);c.setText(tr(label));c.setTextColor(TEXT);c.setPadding(0,dp(6),0,dp(6));return c;}

    private EditText editField(String hint,String value){EditText e=new EditText(this);e.setHint(tr(hint));e.setText(value);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setBackground(makeBg(CARD_2,12,STROKE));e.setPadding(dp(14),dp(12),dp(14),dp(12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(6),0,dp(6));e.setLayoutParams(lp);return e;}

    private Bitmap qr(String value,int size)throws Exception{BitMatrix m=new MultiFormatWriter().encode(value,BarcodeFormat.QR_CODE,size,size);Bitmap b=Bitmap.createBitmap(size,size,Bitmap.Config.RGB_565);for(int y=0;y<size;y++)for(int x=0;x<size;x++)b.setPixel(x,y,m.get(x,y)?Color.BLACK:Color.WHITE);return b;}
    private LinearLayout sectionIntro(String overline,String heading,String sub){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(2),dp(5),dp(2),dp(16));
        LinearLayout tagRow=new LinearLayout(this);tagRow.setOrientation(LinearLayout.HORIZONTAL);tagRow.setGravity(Gravity.CENTER_VERTICAL);View signal=new View(this);signal.setBackgroundColor(NeonPalette.CYAN);tagRow.addView(signal,new LinearLayout.LayoutParams(dp(24),dp(3)));TextView o=tv(overline,10,true,NeonPalette.CYAN);o.setLetterSpacing(.14f);o.setPadding(dp(8),0,0,0);tagRow.addView(o);l.addView(tagRow);
        TextView h=tv(heading,27,true,TEXT);h.setPadding(0,dp(6),0,0);l.addView(h);TextView st=tv(sub,13,false,MUTED);st.setPadding(0,dp(5),0,0);l.addView(st);return l;
    }

    private TextView miniPill(String label,boolean lime){
        TextView p=tv(label.toUpperCase(AppUiPrefs.locale(this)),10,true,lime?PRIMARY_TEXT:TEXT);p.setGravity(Gravity.CENTER);p.setPadding(dp(10),dp(5),dp(10),dp(5));p.setBackground(makeBg(lime?LIME:Color.argb(20,255,255,255),999,lime?LIME:STROKE));return p;
    }

    private TextView statusPill(String status){
        String s=status==null?"":status.toLowerCase(Locale.FRANCE);boolean positive=s.contains("confirm")||s.contains("active")||s.contains("présent")||s.contains("running")||s.contains("valid");
        boolean warning=s.contains("attente")||s.contains("pending")||s.contains("pause");int fill=positive?LIME:warning?ORANGE:Color.argb(22,255,255,255);int color=(positive||warning)?PRIMARY_TEXT:TEXT;
        TextView p=tv(status==null||status.isEmpty()?"Statut":status,10,true,color);p.setGravity(Gravity.CENTER);p.setPadding(dp(9),dp(5),dp(9),dp(5));p.setBackground(makeBg(fill,999,fill));return p;
    }

    private LinearLayout infoRow(String label,String value){
        LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.HORIZONTAL);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(0,dp(9),0,0);
        TextView l=tv(label,12,false,MUTED);line.addView(l,new LinearLayout.LayoutParams(0,-2,1));TextView v=tv(value==null?"":value,13,true,TEXT);v.setGravity(Gravity.RIGHT);line.addView(v,new LinearLayout.LayoutParams(0,-2,1));return line;
    }

    private LinearLayout adminAction(String heading,String sub,String action){
        LinearLayout c=card();c.setPadding(dp(14),dp(14),dp(14),dp(14));c.addView(tv(heading,16,true,TEXT));TextView s=tv(sub,11,false,MUTED);s.setPadding(0,dp(4),0,dp(8));c.addView(s);c.addView(tv("Ouvrir  →",12,true,LIME));
        c.setOnClickListener(v->{
            if("players".equals(action))loadAdminPlayers();
            else if("bookings".equals(action))loadAdminBookings();
            else if("live".equals(action))loadAdminLive();
            else if("scanner".equals(action))startActivity(new Intent(this,ScannerActivity.class));
            else if("notifications".equals(action)){section="Notifications";render();}
            else if("tournaments".equals(action)){section="Tournois";render();}
            else if("memberships".equals(action)){section="Adhésions";render();}
            else if("media".equals(action)){section="Galerie";render();}
            else if("rewards".equals(action)){section="Points";render();}
            else if("automations".equals(action))startActivity(new Intent(this,StudioActivity.class));
            else if("dashboard".equals(action)){section="Admin";render();}
            else if("validation".equals(action))loadAdminTasks();
            else if("confirmations".equals(action))loadAdminConfirmations();
            else if("games".equals(action))loadAdminGeneric("Parties","/api/sessions","Sessions et parties enregistrées.");
            else if("communications".equals(action)){section="Messages";render();}
            else if("payments".equals(action)){section="Finance admin";render();}
            else if("invoices".equals(action))showFinanceCategory("Reçus / factures / avoirs","documents");
            else if("scenarios".equals(action))loadAdminGeneric("Scénarios","/api/scenarios","Missions, règles et recommandations.");
            else if("forms".equals(action))loadAdminGeneric("Formulaires","/api/admin-content","Contenus, formulaires et éléments configurables.");
            else if("stats".equals(action))loadAdminGeneric("Statistiques","/api/operations","Activité et indicateurs opérationnels.");
            else if("newcomers".equals(action))loadAdminMembers();
            else if("participations".equals(action))loadAdminParticipations();
            else if("changes".equals(action))loadAdminBookingChanges();
            else if("blocked".equals(action))loadAdminRestrictions();
            else if("duplicates".equals(action))loadAdminAccountMerge();
            else if("roles".equals(action))loadAdminRoles();
            else if("audit".equals(action))loadAdminAudit();
            else if("backups".equals(action))loadAdminBackups();
            else if("management".equals(action))loadAdminGeneric("Gestion","/api/management","Rôles, blocages, restrictions et sécurité.");
            else if("settings".equals(action))loadAdminSettingsNative();
            else showAdminModule(heading,sub);
        });return c;
    }


    private void loadAdminTasks(){
        adminSubpage=true;
        content.removeAllViews();
        content.addView(sectionIntro("ADMINISTRATION","À valider","Tâches administratives renvoyées par le site. Une tâche peut être marquée terminée ou rouverte."));
        loadJson("/api/platform",d->{
            JSONArray tasks=d.optJSONArray("tasks");
            if(tasks==null||tasks.length()==0){content.addView(cardText("Aucune tâche","Le site ne renvoie actuellement aucune tâche administrative à traiter."));}
            else{
                for(int i=0;i<tasks.length();i++){
                    JSONObject task=tasks.optJSONObject(i);if(task==null)continue;
                    LinearLayout c=adminJsonCard(task);
                    String id=task.optString("id","");boolean completed=task.optBoolean("completed",false);
                    if(!id.isEmpty()){
                        Button toggle=completed?secondaryButton("Rouvrir la tâche"):button("Marquer comme terminée");
                        toggle.setOnClickListener(v->{toggle.setEnabled(false);io.execute(()->{try{JSONObject body=new JSONObject().put("action","toggle_task").put("id",id);SyncApi.post(this,"/api/platform",store.token(),body);runOnUiThread(()->{Toast.makeText(this,completed?"Tâche rouverte":"Tâche terminée",Toast.LENGTH_SHORT).show();loadAdminTasks();});}catch(Exception e){runOnUiThread(()->{toggle.setEnabled(true);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});});
                        c.addView(toggle);
                    }
                    content.addView(c);
                }
            }
            LinearLayout note=card();note.addView(miniPill("ACTIONS RÉELLES",true));note.addView(tv("Seule l’action supportée par l’API est proposée ici : terminer / rouvrir une tâche. Les autres écrans admin restent en consultation lorsqu’aucune action serveur sûre n’existe.",12,false,MUTED));content.addView(note);
            Button back=secondaryButton("← Retour à l’administration");back.setOnClickListener(v->{section="Admin";render();});content.addView(back);
        });
    }


    private void runAdminMutation(String method,String path,JSONObject body,String success,Runnable reload){
        io.execute(()->{try{
            JSONObject result="PATCH".equals(method)?ApiClient.patch(path,store.token(),body):ApiClient.post(path,store.token(),body);
            runOnUiThread(()->{Toast.makeText(this,success,Toast.LENGTH_SHORT).show();if(reload!=null)reload.run();});
        }catch(Exception e){runOnUiThread(()->Toast.makeText(this,safeError(e),Toast.LENGTH_LONG).show());}});
    }

    private void addAdminBack(){
        Button back=secondaryButton("← Retour à l’administration");back.setOnClickListener(v->{section="Admin";render();});content.addView(back);
    }

    private void loadAdminConfirmations(){
        adminSubpage=true;content.removeAllViews();content.addView(sectionIntro("ADMINISTRATION","Confirmations","Confirmations de présence réellement renvoyées par /api/confirmation."));
        loadJson("/api/confirmation",d->{JSONArray a=d.optJSONArray("bookings");
            if(a==null||a.length()==0)content.addView(cardText("Aucune confirmation","Aucune réservation à afficher."));
            else for(int i=0;i<a.length();i++){JSONObject b=a.optJSONObject(i);if(b==null)continue;LinearLayout c=card();String name=join(b.optString("firstName"),b.optString("name"));c.addView(tv(name.isEmpty()?b.optString("reference","Réservation"):name,18,true,TEXT));c.addView(infoRow("Référence",b.optString("reference","—")));c.addView(infoRow("Créneau",b.optString("date","—")+" · "+b.optString("time","—")));c.addView(infoRow("Joueurs",String.valueOf(b.optInt("players",0))));c.addView(infoRow("Réservation",prettyKey(b.optString("status","—"))));String st=b.optString("confirmationStatus","pending");c.addView(infoRow("Confirmation",prettyKey(st)));String token=b.optString("confirmationToken","");
                if(!token.isEmpty()&&(st.equals("pending")||st.equals("sent")||st.equals("waiting"))){LinearLayout r=row();Button ok=button("Confirmer");Button no=secondaryButton("Annuler");ok.setOnClickListener(v->runAdminMutation("PATCH","/api/confirmation",new JSONObjectSafe().putS("token",token).putS("action","confirm").json(),"Confirmation enregistrée",this::loadAdminConfirmations));no.setOnClickListener(v->runAdminMutation("PATCH","/api/confirmation",new JSONObjectSafe().putS("token",token).putS("action","cancel").json(),"Confirmation annulée",this::loadAdminConfirmations));r.addView(ok,new LinearLayout.LayoutParams(0,dp(50),1));r.addView(no,new LinearLayout.LayoutParams(0,dp(50),1));c.addView(r);}content.addView(c);}addAdminBack();});
    }

    private void loadAdminBookingChanges(){
        adminSubpage=true;content.removeAllViews();content.addView(sectionIntro("ADMINISTRATION","Modifications","Demandes de modification. Une erreur 409 laisse la demande en attente et affiche la raison du serveur."));
        loadJson("/api/booking-changes",d->{JSONArray a=d.optJSONArray("changes");if(a==null||a.length()==0)content.addView(cardText("Aucune demande","Aucune modification en attente."));else for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;LinearLayout c=card();c.addView(tv("Réservation "+x.optString("bookingId",""),17,true,TEXT));c.addView(infoRow("Statut",prettyKey(x.optString("status","pending"))));c.addView(infoRow("Avant",compactJson(x.optString("oldValues","{}"))));c.addView(infoRow("Demandé",compactJson(x.optString("requestedValues","{}"))));String id=x.optString("id","");String st=x.optString("status","pending");if(!id.isEmpty()&&"pending".equals(st)){Button approve=button("Approuver");Button reject=secondaryButton("Refuser");approve.setOnClickListener(v->runAdminMutation("PATCH","/api/booking-changes",new JSONObjectSafe().putS("id",id).putS("status","approved").json(),"Modification approuvée",this::loadAdminBookingChanges));reject.setOnClickListener(v->runAdminMutation("PATCH","/api/booking-changes",new JSONObjectSafe().putS("id",id).putS("status","rejected").json(),"Modification refusée",this::loadAdminBookingChanges));c.addView(approve);c.addView(reject);}content.addView(c);}addAdminBack();});
    }

    private String compactJson(String raw){try{JSONObject o=new JSONObject(raw);StringBuilder b=new StringBuilder();java.util.Iterator<String> it=o.keys();while(it.hasNext()){String k=it.next();if(b.length()>0)b.append(" · ");b.append(prettyKey(k)).append(": ").append(String.valueOf(o.opt(k)));}return b.length()==0?"—":b.toString();}catch(Exception e){return raw==null||raw.isEmpty()?"—":raw;}}

    private void loadAdminMembers(){
        adminSubpage=true;content.removeAllViews();content.addView(sectionIntro("ADMINISTRATION","Nouveaux comptes & adhésions","Validation native via les contrats /api/members."));
        loadJson("/api/members",d->{JSONArray members=d.optJSONArray("members");if(members!=null)for(int i=0;i<members.length();i++){JSONObject m=members.optJSONObject(i);if(m==null)continue;LinearLayout c=card();String name=m.optString("displayName",m.optString("name",m.optString("email","Membre")));c.addView(tv(name,17,true,TEXT));c.addView(infoRow("E-mail",m.optString("email","—")));String status=m.optString("status","pending");c.addView(infoRow("Statut",prettyKey(status)));String id=m.optString("id","");if(!id.isEmpty()){Button approve=button("Approuver");approve.setOnClickListener(v->setMemberStatus(id,"approved"));Button suspend=secondaryButton("Suspendre");suspend.setOnClickListener(v->setMemberStatus(id,"suspended"));c.addView(approve);c.addView(suspend);}content.addView(c);}JSONArray memberships=d.optJSONArray("memberships");if(memberships!=null&&memberships.length()>0){content.addView(cardText("Adhésions","Demandes d’adhésion : "+memberships.length()));for(int i=0;i<memberships.length();i++){JSONObject m=memberships.optJSONObject(i);if(m==null)continue;LinearLayout c=adminJsonCard(m);String id=m.optString("id",m.optString("membershipId",""));String st=m.optString("status","pending");if(!id.isEmpty()&&"pending".equals(st)){Button active=button("Activer l’adhésion");active.setOnClickListener(v->setMembershipStatus(id,"active"));Button reject=secondaryButton("Refuser");reject.setOnClickListener(v->setMembershipStatus(id,"rejected"));c.addView(active);c.addView(reject);}content.addView(c);}}addAdminBack();});
    }
    private void setMemberStatus(String id,String status){runAdminMutation("PATCH","/api/members",new JSONObjectSafe().putS("id",id).putS("status",status).json(),"Compte mis à jour",this::loadAdminMembers);}
    private void setMembershipStatus(String id,String status){runAdminMutation("PATCH","/api/members",new JSONObjectSafe().putS("membershipId",id).putS("status",status).json(),"Adhésion mise à jour",this::loadAdminMembers);}

    private void loadAdminRestrictions(){
        adminSubpage=true;content.removeAllViews();content.addView(sectionIntro("ADMINISTRATION","Absences & blocages","Actions sensibles envoyées directement au serveur, sans file de rejeu automatique."));
        loadJson("/api/reliability",d->{JSONArray a=d.optJSONArray("participants");if(a==null||a.length()==0)content.addView(cardText("Aucun participant","Aucune donnée participant disponible dans /api/reliability."));else for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;LinearLayout c=adminJsonCard(x);String uid=x.optString("userId",x.optString("user_id",""));if(!uid.isEmpty()){Button absence=secondaryButton("+1 absence");absence.setOnClickListener(v->restriction(uid,"add_absence"));Button reset=secondaryButton("Réinitialiser absences");reset.setOnClickListener(v->restriction(uid,"reset_absences"));Button block=secondaryButton("Bloquer les réservations");block.setOnClickListener(v->restriction(uid,"block_booking"));Button allow=secondaryButton("Autoriser les réservations");allow.setOnClickListener(v->restriction(uid,"allow_booking"));c.addView(absence);c.addView(reset);c.addView(block);c.addView(allow);}content.addView(c);}addAdminBack();});
    }
    private void restriction(String userId,String action){runAdminMutation("POST","/api/admin-restrictions",new JSONObjectSafe().putS("userId",userId).putS("action",action).json(),"Restriction mise à jour",this::loadAdminRestrictions);}

    private void loadAdminRoles(){
        adminSubpage=true;content.removeAllViews();content.addView(sectionIntro("ADMINISTRATION","Rôles & permissions","Réservé au propriétaire. Attribution admin/organizer selon le contrat serveur."));Button grant=button("Attribuer un rôle");grant.setOnClickListener(v->showGrantRoleDialog());content.addView(grant);
        loadJson("/api/admins",d->{JSONArray staff=d.optJSONArray("staff");if(staff==null||staff.length()==0)content.addView(cardText("Aucun rôle","Aucun membre du staff renvoyé."));else for(int i=0;i<staff.length();i++){JSONObject x=staff.optJSONObject(i);if(x==null)continue;LinearLayout c=adminJsonCard(x);String id=x.optString("id","");if(!id.isEmpty()){Button revoke=secondaryButton("Retirer ce rôle");revoke.setOnClickListener(v->runAdminMutation("POST","/api/admins",new JSONObjectSafe().putS("action","revoke").putS("id",id).json(),"Rôle retiré",this::loadAdminRoles));c.addView(revoke);}content.addView(c);}addAdminBack();});
    }
    private void showGrantRoleDialog(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(22),dp(10),dp(22),0);EditText email=new EditText(this);email.setHint("E-mail du compte");email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);EditText role=new EditText(this);role.setHint("admin ou organizer");role.setText("admin");box.addView(email);box.addView(role);new AlertDialog.Builder(this).setTitle("Attribuer un rôle").setView(box).setNegativeButton("Annuler",null).setPositiveButton("Attribuer",(d,w)->{String e=email.getText().toString().trim();String r=role.getText().toString().trim().toLowerCase(Locale.FRANCE);if(!r.equals("admin")&&!r.equals("organizer")){Toast.makeText(this,"Rôle : admin ou organizer",Toast.LENGTH_LONG).show();return;}runAdminMutation("POST","/api/admins",new JSONObjectSafe().putS("action","grant").putS("email",e).putS("role",r).json(),"Rôle attribué",this::loadAdminRoles);}).show();}


    private void loadAdminParticipations(){
        adminSubpage=true;content.removeAllViews();
        content.addView(sectionIntro("ADMINISTRATION","Participations","Accepter ou refuser les demandes de parties ouvertes. La capacité est recontrôlée atomiquement côté D1."));
        loadJson("/api/participations",d->{
            JSONArray a=d.optJSONArray("participations");
            if(a==null||a.length()==0)content.addView(cardText("Aucune participation","Aucune demande de participation n’est actuellement enregistrée."));
            else for(int i=0;i<a.length();i++){
                JSONObject x=a.optJSONObject(i);if(x==null)continue;
                LinearLayout c=card();
                String name=x.optString("displayName","Joueur");String status=x.optString("status","requested");
                c.addView(tv(name,18,true,TEXT));
                c.addView(infoRow("Partie",x.optString("gameTitle",x.optString("gameId","—"))));
                c.addView(infoRow("Créneau",x.optString("gameDate","—")+" · "+x.optString("startTime","—")));
                c.addView(infoRow("Statut",prettyKey(status)));
                c.addView(infoRow("Capacité",String.valueOf(x.optInt("capacity",20))));
                String id=x.optString("id","");
                if(!id.isEmpty()&&!"confirmed".equals(status)&&!"rejected".equals(status)){
                    LinearLayout actions=row();
                    Button accept=button("Accepter");Button reject=secondaryButton("Refuser");
                    accept.setOnClickListener(v->runAdminMutation("PATCH","/api/participations",new JSONObjectSafe().putS("id",id).putS("decision","accept").json(),"Participation acceptée",this::loadAdminParticipations));
                    reject.setOnClickListener(v->showParticipationRejectDialog(id));
                    actions.addView(accept,new LinearLayout.LayoutParams(0,dp(50),1));actions.addView(reject,new LinearLayout.LayoutParams(0,dp(50),1));c.addView(actions);
                }
                content.addView(c);
            }
            addAdminBack();
        });
    }

    private void showParticipationRejectDialog(String id){
        EditText reason=editField("Motif facultatif","");
        new AlertDialog.Builder(this).setTitle("Refuser la participation").setView(reason).setNegativeButton("Annuler",null).setPositiveButton("Refuser",(d,w)->
            runAdminMutation("PATCH","/api/participations",new JSONObjectSafe().putS("id",id).putS("decision","reject").putS("reason",reason.getText().toString().trim()).json(),"Participation refusée",this::loadAdminParticipations)
        ).show();
    }

    private boolean ownerToolsAllowed(){
        return isOwnerAccount(snapshot);
    }

    private void loadAdminAccountMerge(){
        adminSubpage=true;content.removeAllViews();
        content.addView(sectionIntro("ADMINISTRATION","Fusion de doubles comptes","Outil propriétaire. Prévisualisation et sauvegarde obligatoires avant toute fusion."));
        if(!ownerToolsAllowed()){
            content.addView(cardText("Accès propriétaire","La fusion de comptes est volontairement réservée au propriétaire du site."));addAdminBack();return;
        }
        LinearLayout warning=card();warning.addView(miniPill("ACTION SENSIBLE",false));warning.addView(tv("Le compte cible est conservé comme compte principal. En cas de doublon interne, l’application te demandera explicitement de garder la donnée déjà présente sur le compte cible. Une sauvegarde JSON est enregistrée avant la fusion.",13,false,MUTED));content.addView(warning);
        EditText source=editField("ID du compte doublon à absorber","");
        EditText target=editField("ID du compte principal à conserver","");
        content.addView(source);content.addView(target);
        Button preview=button("Prévisualiser + sauvegarder");content.addView(preview);
        preview.setOnClickListener(v->{String s=source.getText().toString().trim(),t=target.getText().toString().trim();if(s.length()<3||t.length()<3||s.equals(t)){Toast.makeText(this,"Renseigne deux identifiants de comptes distincts.",Toast.LENGTH_LONG).show();return;}preview.setEnabled(false);previewAccountMerge(s,t,preview);});
        addAdminBack();
    }

    private void previewAccountMerge(String source,String target,Button trigger){
        io.execute(()->{try{
            JSONObject payload=new JSONObjectSafe().putS("action","preview").putS("sourceUserId",source).putS("targetUserId",target).json();
            JSONObject result=ApiClient.post("/api/account-merge",store.token(),payload);
            JSONObject backup=result.optJSONObject("backup");if(backup==null)throw new Exception("Le serveur n’a pas renvoyé la sauvegarde obligatoire.");
            JSONArray changes=result.optJSONArray("changes"), conflicts=result.optJSONArray("conflicts");
            pendingMergeSource=source;pendingMergeTarget=target;pendingMergeChangeCount=changes==null?0:changes.length();pendingMergeConflictCount=conflicts==null?0:conflicts.length();pendingAdminBackupJson=backup.toString();
            runOnUiThread(()->{trigger.setEnabled(true);Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"unite-paintball-avant-fusion-"+new SimpleDateFormat("yyyy-MM-dd-HHmmss",Locale.FRANCE).format(new Date())+".json");startActivityForResult(i,REQ_ADMIN_BACKUP_SAVE);});
        }catch(Exception e){runOnUiThread(()->{trigger.setEnabled(true);Toast.makeText(this,safeError(e),Toast.LENGTH_LONG).show();});}});
    }

    private void confirmAccountMergeAfterBackup(){
        if(pendingMergeSource.isEmpty()||pendingMergeTarget.isEmpty())return;
        String msg="Source : "+pendingMergeSource+"\nCible : "+pendingMergeTarget+"\nRéférences touchées : "+pendingMergeChangeCount;
        if(pendingMergeConflictCount>0)msg+="\n\n⚠ "+pendingMergeConflictCount+" conflit(s) d’unicité : les doublons correspondants du compte source seront supprimés et la donnée du compte cible sera conservée.";
        final String message=msg;
        new AlertDialog.Builder(this).setTitle("Confirmer la fusion").setMessage(message).setNegativeButton("Annuler",null).setPositiveButton("FUSIONNER",(d,w)->performAccountMerge()).show();
    }

    private void performAccountMerge(){
        final String source=pendingMergeSource,target=pendingMergeTarget;final int conflicts=pendingMergeConflictCount;
        io.execute(()->{try{
            JSONObject body=new JSONObjectSafe().putS("action","merge").putS("sourceUserId",source).putS("targetUserId",target).putS("confirm","FUSIONNER").putS("backupSaved",true).putS("conflictPolicy",conflicts>0?"keep_target":"none").json();
            JSONObject result=ApiClient.post("/api/account-merge",store.token(),body);
            pendingMergeSource="";pendingMergeTarget="";pendingMergeConflictCount=0;pendingMergeChangeCount=0;
            runOnUiThread(()->{Toast.makeText(this,"Fusion terminée · alias créé · "+result.optInt("updatedEstimate",0)+" référence(s) examinée(s)",Toast.LENGTH_LONG).show();loadAdminAccountMerge();});
        }catch(Exception e){runOnUiThread(()->Toast.makeText(this,safeError(e),Toast.LENGTH_LONG).show());}});
    }

    private void loadAdminBackups(){
        adminSubpage=true;content.removeAllViews();
        content.addView(sectionIntro("ADMINISTRATION","Sauvegardes","Export et restauration D1 + médias. Outil propriétaire uniquement."));
        if(!ownerToolsAllowed()){
            content.addView(cardText("Accès propriétaire","Les sauvegardes complètes contiennent des données personnelles et sont réservées au propriétaire."));addAdminBack();return;
        }
        LinearLayout explain=card();explain.addView(tv("Sauvegarde complète",20,true,TEXT));explain.addView(tv("L’export contient D1 et les médias R2 dans un JSON. La restauration peut soit laisser les médias en staging, soit réactiver les clés médias sauvegardées. D1 est transactionnel ; D1 et R2 ne peuvent pas partager une transaction globale.",13,false,MUTED));content.addView(explain);
        Button export=button("Exporter D1 + médias");export.setOnClickListener(v->exportAdminBackup(export));content.addView(export);
        Button restore=secondaryButton("Restaurer depuis un fichier JSON");restore.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");startActivityForResult(i,REQ_ADMIN_BACKUP_RESTORE);});content.addView(restore);
        addAdminBack();
    }

    private void exportAdminBackup(Button trigger){
        pendingMergeSource="";pendingMergeTarget="";pendingMergeConflictCount=0;pendingMergeChangeCount=0;
        trigger.setEnabled(false);io.execute(()->{try{JSONObject backup=ApiClient.get("/api/admin-backups",store.token());pendingAdminBackupJson=backup.toString();runOnUiThread(()->{trigger.setEnabled(true);Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"unite-paintball-backup-"+new SimpleDateFormat("yyyy-MM-dd-HHmmss",Locale.FRANCE).format(new Date())+".json");startActivityForResult(i,REQ_ADMIN_BACKUP_SAVE);});}catch(Exception e){runOnUiThread(()->{trigger.setEnabled(true);Toast.makeText(this,safeError(e),Toast.LENGTH_LONG).show();});}});
    }

    private String readUriText(Uri uri)throws Exception{
        try(java.io.InputStream in=getContentResolver().openInputStream(uri);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            if(in==null)throw new java.io.IOException("Fichier inaccessible");byte[] buf=new byte[65536];int n;while((n=in.read(buf))>0){out.write(buf,0,n);if(out.size()>96*1024*1024)throw new java.io.IOException("Sauvegarde trop volumineuse pour le téléphone");}return out.toString("UTF-8");
        }
    }

    private void confirmRestoreAdminBackup(JSONObject backup){
        new AlertDialog.Builder(this).setTitle("Restaurer la sauvegarde ?").setMessage("Cette opération remplace les données D1 par le contenu du fichier. Choisis « staging » pour restaurer les médias sans toucher aux clés actives, ou « activer » pour réécrire aussi les médias sauvegardés.")
            .setNegativeButton("Annuler",null)
            .setNeutralButton("D1 + médias en staging",(d,w)->performRestoreAdminBackup(backup,"staged"))
            .setPositiveButton("D1 + activer médias",(d,w)->performRestoreAdminBackup(backup,"replace"))
            .show();
    }

    private void performRestoreAdminBackup(JSONObject backup,String mediaMode){
        io.execute(()->{try{JSONObject body=new JSONObjectSafe().putS("confirm","RESTAURER").putS("backup",backup).putS("mediaMode",mediaMode).json();JSONObject result=ApiClient.post("/api/admin-backups",store.token(),body);runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Restauration terminée").setMessage("Tables : "+result.optInt("tables",0)+"\nLignes : "+result.optInt("rows",0)+"\nMédias : "+result.optInt("media",0)+"\nMode médias : "+result.optString("mediaMode",mediaMode)+"\n\n"+result.optString("warning","")).setPositiveButton("OK",null).show());}catch(Exception e){runOnUiThread(()->Toast.makeText(this,safeError(e),Toast.LENGTH_LONG).show());}});
    }

    private void loadAdminAudit(){adminSubpage=true;content.removeAllViews();content.addView(sectionIntro("ADMINISTRATION","Journal admin","Journal en lecture seule via /api/admin-audit."));loadJson("/api/admin-audit",d->{JSONArray a=d.optJSONArray("audit");if(a==null||a.length()==0)content.addView(cardText("Journal vide","Aucune action enregistrée."));else for(int i=0;i<Math.min(a.length(),200);i++){JSONObject x=a.optJSONObject(i);if(x!=null)content.addView(adminJsonCard(x));}addAdminBack();});}

    private void loadAdminSettingsNative(){
        adminSubpage=true;content.removeAllViews();content.addView(sectionIntro("ADMINISTRATION","Paramètres","Valeurs réelles de /api/admin-settings. La sauvegarde d’une valeur de référence ne déclenche pas automatiquement une tâche serveur."));
        loadJson("/api/admin-settings",d->{JSONObject st=d.optJSONObject("settings");if(st==null){content.addView(cardText("Paramètres indisponibles","Le serveur n’a pas renvoyé l’objet settings."));addAdminBack();return;}java.util.ArrayList<String> keys=new java.util.ArrayList<>();java.util.Iterator<String> it=st.keys();while(it.hasNext())keys.add(it.next());java.util.Collections.sort(keys);for(String key:keys){String value=String.valueOf(st.opt(key));LinearLayout c=card();c.addView(tv(prettyKey(key),16,true,TEXT));c.addView(infoRow("Valeur",value));Button edit=secondaryButton("Modifier");edit.setOnClickListener(v->showSettingDialog(key,value));c.addView(edit);content.addView(c);}addAdminBack();});
    }
    private void showSettingDialog(String key,String current){EditText input=new EditText(this);input.setText(current);input.setSelection(input.length());new AlertDialog.Builder(this).setTitle(prettyKey(key)).setView(input).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",(d,w)->runAdminMutation("POST","/api/admin-settings",new JSONObjectSafe().putS("key",key).putS("value",input.getText().toString().trim()).json(),"Paramètre enregistré",this::loadAdminSettingsNative)).show();}

    private static final class JSONObjectSafe {
        private final JSONObject o=new JSONObject();
        JSONObjectSafe putS(String k,Object v){try{o.put(k,v);}catch(Exception ignored){}return this;}
        JSONObject json(){return o;}
    }

    private void loadAdminGeneric(String heading,String endpoint,String sub){
        openSharedFeature(SharedFeaturePolicy.pageForApi(endpoint));
    }

    private void loadAdminGenericLegacy(String heading,String endpoint,String sub){
        adminSubpage=true;
        content.removeAllViews();
        content.addView(sectionIntro("ADMINISTRATION",heading,sub));
        LinearLayout wait=card();wait.addView(miniPill("SYNCHRONISATION",true));wait.addView(tv("Chargement des données…",16,true,TEXT));content.addView(wait);
        loadJson(endpoint,d->{
            if(content.getChildCount()>1) content.removeViewAt(1);
            LinearLayout meta=card();meta.addView(miniPill("CONSULTATION",false));
            meta.addView(tv(heading,22,true,TEXT));
            meta.addView(tv("Cet écran affiche les données réellement renvoyées par l’API. Aucun bouton de modification n’est affiché tant que l’API correspondante ne fournit pas une action d’écriture vérifiée.",12,false,MUTED));
            String siteAdminPath=adminSitePathFor(heading);
            if(siteAdminPath!=null){
                Button fullSite=secondaryButton("Ouvrir la gestion complète sur le site");
                fullSite.setOnClickListener(v->openSitePath(siteAdminPath));
                meta.addView(fullSite);
            }
            content.addView(meta);
            int shown=0;
            java.util.Iterator<String> keys=d.keys();
            while(keys.hasNext() && shown<12){
                String k=keys.next();Object v=d.opt(k);
                if(v instanceof org.json.JSONArray){
                    org.json.JSONArray a=(org.json.JSONArray)v;
                    LinearLayout sec=card();sec.addView(tv(prettyKey(k),17,true,TEXT));sec.addView(tv(a.length()+" élément(s)",12,false,MUTED));content.addView(sec);
                    for(int i=0;i<Math.min(a.length(),8);i++){
                        Object item=a.opt(i);if(item instanceof org.json.JSONObject)content.addView(adminJsonCard((org.json.JSONObject)item));
                        else content.addView(cardText(String.valueOf(item),""));
                    }
                    shown++;
                }else if(v instanceof org.json.JSONObject){
                    content.addView(adminJsonCard((org.json.JSONObject)v));shown++;
                }else if(v!=null && v!=org.json.JSONObject.NULL){
                    content.addView(cardText(prettyKey(k),String.valueOf(v)));shown++;
                }
            }
            Button back=secondaryButton("← Retour à l’administration");back.setOnClickListener(v->{section="Admin";render();});content.addView(back);
        });
    }


    private String adminSitePathFor(String heading){
        if(heading==null)return "/admin";
        switch(heading){
            case "Tableau de bord": return "/admin";
            case "Confirmations": return "/admin/confirmations";
            case "Parties": return "/admin/parties";
            case "Communications": return "/admin/communications";
            case "Paiements": return "/admin/commercial";
            case "Factures": return "/admin/comptabilite";
            case "Scénarios": return "/admin/parties";
            case "Formulaires": return "/admin/informations";
            case "Statistiques": return "/admin/statistiques";
            case "Nouveaux arrivants": return "/admin/nouveaux-arrivants";
            case "Participations": return "/admin/participations";
            case "Modifications": return "/admin/modifications";
            case "Comptes bloqués": return "/admin/absences";
            case "Doubles comptes": return "/admin/fiabilite";
            case "Rôles & permissions": return "/admin/administrateurs";
            case "Journal admin": return "/admin/historique";
            case "Sauvegardes": return "/admin/editeur/sauvegardes";
            case "Gestion": return "/admin/gestion";
            case "Paramètres": return "/admin/parametres";
            default: return "/admin";
        }
    }

    private LinearLayout adminJsonCard(JSONObject o){
        LinearLayout c=card();String title=o.optString("name",o.optString("title",o.optString("displayName",o.optString("reference",o.optString("email","Élément")))));c.addView(tv(title,16,true,TEXT));
        int n=0;java.util.Iterator<String> it=o.keys();while(it.hasNext()&&n<8){String k=it.next();if(isTechnicalAdminKey(k))continue;Object v=o.opt(k);if(v==null||v==JSONObject.NULL||v instanceof JSONObject||v instanceof JSONArray)continue;String val=String.valueOf(v);if(val.length()>90)val=val.substring(0,87)+"…";c.addView(infoRow(prettyKey(k),val));n++;}return c;
    }
    private boolean isTechnicalAdminKey(String key){
        if(key==null)return false;String k=key.toLowerCase(Locale.FRANCE).replace("_","").replace("-","");
        return k.equals("id")||k.endsWith("id")||k.contains("token")||k.contains("secret")||k.contains("apikey")||k.contains("password")||k.contains("hash")||k.contains("session")||k.contains("endpoint")||k.contains("url");
    }
    private String prettyKey(String k){if(k==null)return "";return k.replace('_',' ').replace('-', ' ');}

    private void showAdminModule(String heading,String sub){
        adminSubpage=true;
        content.removeAllViews();
        content.addView(sectionIntro("ADMINISTRATION",heading,sub));
        LinearLayout info=card();info.addView(miniPill("MODULE ADMIN",true));TextView h=tv(heading,22,true,TEXT);h.setPadding(0,dp(10),0,0);info.addView(h);info.addView(tv("Ce module fait partie du tableau de bord administrateur natif. Les données disponibles seront synchronisées avec les API existantes du site.",13,false,MUTED));content.addView(info);
        Button back=secondaryButton("← Retour à l’administration");back.setOnClickListener(v->{section="Admin";render();});content.addView(back);
    }

    private JSONArray extractArray(JSONObject root,String... keys){
        if(root==null)return null;
        for(String k:keys){JSONArray a=root.optJSONArray(k);if(a!=null)return a;}
        String[] containers={"data","result","payload","response"};
        for(String c:containers){JSONObject nested=root.optJSONObject(c);if(nested!=null){for(String k:keys){JSONArray a=nested.optJSONArray(k);if(a!=null)return a;}}}
        // Dernier recours : première liste d'objets suffisamment plausible renvoyée par l'API.
        java.util.Iterator<String> it=root.keys();
        while(it.hasNext()){String k=it.next();JSONArray a=root.optJSONArray(k);if(a!=null&&a.length()>0&&a.optJSONObject(0)!=null)return a;}
        return null;
    }

    private String initials(String text){
        if(text==null||text.trim().isEmpty())return "UP";String[] p=text.trim().split("\\s+");String a=p[0].substring(0,1);String b=p.length>1?p[p.length-1].substring(0,1):"";return (a+b).toUpperCase(Locale.FRANCE);
    }

    private String shortDate(String date){
        if(date==null||date.isEmpty())return "DATE";try{java.text.SimpleDateFormat in=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.FRANCE);java.text.SimpleDateFormat out=new java.text.SimpleDateFormat("dd MMM",Locale.FRANCE);java.util.Date d=in.parse(date);return d==null?date:out.format(d).toUpperCase(Locale.FRANCE);}catch(Exception e){return date;}
    }

    private LinearLayout homeAction(String title,String subtitle,String target){LinearLayout l=card();l.setPadding(dp(14),dp(14),dp(14),dp(14));TextView h=tv(title,16,true,TEXT);l.addView(h);TextView sub=tv(subtitle,11,false,MUTED);sub.setPadding(0,dp(4),0,dp(8));l.addView(sub);TextView arrow=tv("Ouvrir  →",12,true,LIME);l.addView(arrow);l.setOnClickListener(v->{section=target;render();});return l;}
    private Button orangeButton(String s){Button b=new Button(this);b.setText(tr(s));b.setAllCaps(false);b.setTextColor(Color.rgb(12,22,18));b.setTypeface(null,Typeface.BOLD);b.setTextSize(14);b.setBackground(makeBg(NeonPalette.CYAN,999,NeonPalette.CYAN));b.setPadding(dp(14),0,dp(14),0);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));lp.setMargins(0,dp(6),0,dp(8));b.setLayoutParams(lp);return b;}
    private TextView filterPill(String s){TextView t=tv(s,14,false,TEXT);t.setGravity(Gravity.CENTER);t.setBackground(makeBg(Color.TRANSPARENT,999,STROKE));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(46));lp.setMargins(dp(4),dp(10),dp(4),0);t.setLayoutParams(lp);return t;}
    private TextView tabLabel(String s,boolean active){TextView t=tv(s,15,true,active?TEXT:MUTED);t.setGravity(Gravity.CENTER);return t;}

    private Button secondaryButton(String s){Button b=new Button(this);b.setText(tr(s));b.setAllCaps(false);b.setTextColor(NeonPalette.CYAN_BRIGHT);b.setTypeface(null,Typeface.BOLD);b.setTextSize(13);b.setBackground(makeBg(Color.argb(22,32,229,243),12,NeonPalette.CYAN));b.setPadding(dp(14),0,dp(14),0);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(46));lp.setMargins(0,dp(10),0,dp(14));b.setLayoutParams(lp);return b;}
    private LinearLayout hero(String a,String b){LinearLayout l=card();TextView over=tv("UNITÉ PAINTBALL",10,true,LIME);over.setLetterSpacing(0.08f);l.addView(over);TextView t=tv(a,27,true,TEXT);t.setPadding(0,dp(6),0,0);l.addView(t);TextView s=tv(b,14,false,MUTED);s.setPadding(0,dp(8),0,0);l.addView(s);View bar=new View(this);bar.setBackgroundColor(LIME);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(56),dp(4));bp.setMargins(0,dp(14),0,0);l.addView(bar,bp);return l;}
    private LinearLayout cardText(String a,String b){LinearLayout l=card();TextView h=tv(a,18,true,TEXT);l.addView(h);TextView s=tv(b,14,false,MUTED);s.setPadding(0,dp(8),0,0);l.addView(s);return l;}
    private LinearLayout stat(String a,String b){LinearLayout l=card();TextView n=tv(b,26,true,LIME);n.setGravity(Gravity.CENTER);l.addView(n);TextView t=tv(a,12,false,MUTED);t.setGravity(Gravity.CENTER);l.addView(t);return l;}
    private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setBackground(makeBg(CARD,16,STROKE));l.setElevation(dp(4));l.setPadding(dp(16),dp(16),dp(16),dp(16));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(12));l.setLayoutParams(p);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);GradientDrawable spacer=new GradientDrawable();spacer.setColor(Color.TRANSPARENT);spacer.setSize(dp(10),1);l.setDividerDrawable(spacer);l.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);return l;}
    private LinearLayout.LayoutParams weight(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(0,0,dp(6),dp(12));return p;}
    private TextView tv(String s,int sp,boolean bold,int color){TextView v=new TextView(this);v.setText(tr(s));v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(null,android.graphics.Typeface.BOLD);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(tr(s));b.setAllCaps(false);b.setTextColor(NeonPalette.PRIMARY_TEXT);b.setTypeface(null,Typeface.BOLD);b.setTextSize(14);b.setBackground(makeGradientBg(NeonPalette.CYAN,NeonPalette.GREEN,12,NeonPalette.CYAN_BRIGHT));b.setPadding(dp(14),0,dp(14),0);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));lp.setMargins(0,dp(10),0,dp(14));b.setLayoutParams(lp);return b;}
    private Button iconButton(String s){Button b=new Button(this);b.setText(tr(s));b.setAllCaps(false);b.setTextColor(TEXT);b.setTextSize(22);b.setBackground(makeBg(Color.TRANSPARENT,12,0));b.setPadding(0,0,0,0);return b;}
    private GradientDrawable makeBg(int fill,int radiusDp,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radiusDp));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private GradientDrawable makeGradientBg(int start,int end,int radiusDp,int stroke){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{start,end});d.setCornerRadius(dp(radiusDp));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private String join(String a,String b){return (a+" "+b).trim();}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void auth(){if(diskCache!=null)diskCache.clear();SyncApi.clear(this);MediaSyncQueue.clear(this);store.clearToken();startActivity(new Intent(this,AuthActivity.class));finish();}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        if(bookingGiftScan){com.google.zxing.integration.android.IntentResult scan=com.google.zxing.integration.android.IntentIntegrator.parseActivityResult(requestCode,resultCode,data);if(scan!=null){bookingGiftScan=false;if(scan.getContents()!=null&&bookingGiftInput!=null&&"Nouvelle réservation".equals(section))bookingGiftInput.setText(scan.getContents());return;}}
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            if(requestCode==REQ_ACCOUNTING_EXPORT){
                try(java.io.OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new java.io.IOException("Destination inaccessible");out.write(pendingAccountingCsv.getBytes(java.nio.charset.StandardCharsets.UTF_8));out.flush();Toast.makeText(this,"Export CSV enregistré",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"Export impossible : "+e.getMessage(),Toast.LENGTH_LONG).show();}finally{pendingAccountingCsv="";}return;
            }
            if(requestCode==REQ_PRIVACY_EXPORT){
                try(java.io.OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new java.io.IOException("Destination inaccessible");out.write(pendingPrivacyJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));out.flush();Toast.makeText(this,"Export de tes données enregistré",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"Export impossible : "+e.getMessage(),Toast.LENGTH_LONG).show();}finally{pendingPrivacyJson="";}return;
            }
            if(requestCode==REQ_ACCOUNTING_DOCUMENT_SAVE){
                try(java.io.OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new java.io.IOException("Destination inaccessible");if(pendingAccountingDocumentBytes==null)throw new java.io.IOException("Document absent");out.write(pendingAccountingDocumentBytes);out.flush();Toast.makeText(this,"Justificatif enregistré",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"Enregistrement impossible : "+e.getMessage(),Toast.LENGTH_LONG).show();}finally{pendingAccountingDocumentBytes=null;pendingAccountingDocumentName="document-comptable";pendingAccountingDocumentMime="application/octet-stream";}return;
            }
            if(requestCode==REQ_ADMIN_BACKUP_SAVE){
                boolean mergeAfter=!pendingMergeSource.isEmpty()&&!pendingMergeTarget.isEmpty();
                try(java.io.OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new java.io.IOException("Destination inaccessible");if(pendingAdminBackupJson.isEmpty())throw new java.io.IOException("Sauvegarde absente");out.write(pendingAdminBackupJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));out.flush();Toast.makeText(this,"Sauvegarde JSON enregistrée",Toast.LENGTH_LONG).show();pendingAdminBackupJson="";if(mergeAfter)confirmAccountMergeAfterBackup();}catch(Exception e){Toast.makeText(this,"Sauvegarde impossible : "+e.getMessage(),Toast.LENGTH_LONG).show();}return;
            }
            if(requestCode==REQ_ADMIN_BACKUP_RESTORE){
                try{String text=readUriText(uri);JSONObject backup=new JSONObject(text);confirmRestoreAdminBackup(backup);}catch(Exception e){Toast.makeText(this,"Sauvegarde invalide : "+e.getMessage(),Toast.LENGTH_LONG).show();}return;
            }
            try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            if(requestCode==REQ_GALLERY_IMAGE)uploadGalleryImage(uri);
            else if(requestCode==REQ_GALLERY_VIDEO)uploadGalleryVideo(uri);
            else if(requestCode==REQ_CHAT_IMAGE)uploadChatImage(uri);
            else if(requestCode==REQ_AVATAR_IMAGE)uploadAvatarImage(uri);
        }
    }

    private void registerSystemBackHandler(){
        if(Build.VERSION.SDK_INT>=33 && systemBackCallback==null){
            systemBackCallback=this::handleBackNavigation;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,systemBackCallback);
        }
    }

    private void unregisterSystemBackHandler(){
        if(Build.VERSION.SDK_INT>=33 && systemBackCallback!=null){
            try{getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(systemBackCallback);}catch(Exception ignored){}
            systemBackCallback=null;
        }
    }

    private void handleBackNavigation(){
        if(startupLoading){finish();return;}
        if(adminSubpage){adminSubpage=false;section="Admin";restoringHistory=true;render();return;}
        if(!activeChatChannel.isEmpty()){activeChatChannel="";activeChatLabel="";activeChatSignature="";handler.removeCallbacks(chatSync);title.setText("Messages");renderMessages();return;}
        if(!sectionHistory.isEmpty()){restoringHistory=true;section=sectionHistory.pop();render();return;}
        if(!"Accueil".equals(section)){restoringHistory=true;section="Accueil";render();return;}
        finish();
    }

    @Override protected void onResume(){
        super.onResume();
        handler.removeCallbacks(autoSync);
        handler.postDelayed(autoSync,1000);
        if(!activeChatChannel.isEmpty()){handler.removeCallbacks(chatSync);handler.postDelayed(chatSync,CHAT_SYNC_MS);}
        long now=System.currentTimeMillis();
        if(store!=null&&!store.token().isEmpty()){
            loadPublishedStudioRuntime();
            FirebasePushManager.ensureRegistered(this);
            if(!startupLoading&&(lastSnapshotRefreshAt==0L||now-lastSnapshotRefreshAt>=SNAPSHOT_RESUME_REFRESH_MS))refresh(false);
        }
    }
    @Override protected void onPause(){super.onPause();handler.removeCallbacks(autoSync);handler.removeCallbacks(chatSync);}
    @Override protected void onStop(){super.onStop();if(store!=null&&!store.token().isEmpty())BackgroundSyncScheduler.scheduleSoon(this);}
    @Override public void onBackPressed(){ handleBackNavigation(); }
    @Override protected void onDestroy(){
        unregisterSystemBackHandler();
        stopStartupMusic();
        super.onDestroy();
        handler.removeCallbacks(chatSync);handler.removeCallbacks(autoSync);io.shutdownNow();prefetchIo.shutdownNow();
    }
}
