package com.unitepaintball.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.view.Gravity;
import android.window.OnBackInvokedCallback;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Coquille volontairement limitée à l'authentification / inscription du site.
 * Le site existant reste inchangé. Dès que le profil joueur est réellement créé,
 * la WebView disparaît et toute l'expérience repasse dans l'application native.
 */
public class AuthActivity extends Activity {
    private static int BG = NeonPalette.BG;
    private static int LIME = NeonPalette.GREEN;
    private static int TEXT = NeonPalette.TEXT;
    private static int SURFACE = NeonPalette.SURFACE;
    private static int MUTED = NeonPalette.MUTED;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private WebView web;
    private TextView status;
    private ProgressBar progress;
    private volatile boolean checking = false;
    private volatile boolean completed = false;
    private String pendingTargetUrl = "";
    private String pendingNotificationCategory = "";
    private OnBackInvokedCallback systemBackCallback;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUiPrefs.migrateToSiteThemeSync(this);
        SiteThemeManager.applyCached(this);
        BG=AppUiPrefs.bg(this);LIME=AppUiPrefs.primary(this);TEXT=AppUiPrefs.text(this);SURFACE=AppUiPrefs.card(this);MUTED=AppUiPrefs.muted(this);
        AppUiPrefs.applyWindow(this);
        Intent source = getIntent();
        if (source != null) {
            String target = source.getStringExtra("target_url");
            String category = source.getStringExtra("notification_category");
            pendingTargetUrl = target == null ? "" : target;
            pendingNotificationCategory = category == null ? "" : category;
        }
        buildUi();
        registerSystemBackHandler();
        openExistingSiteAuthentication();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        header.setPadding(dp(18), dp(10), dp(18), dp(12));
        header.setBackground(bg(BG,0,Color.argb(28,255,255,255)));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.unite_paintball_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER);
        header.addView(logo, new LinearLayout.LayoutParams(dp(132), dp(64)));
        TextView over = new TextView(this);
        over.setText(AppUiPrefs.t(this,"Espace joueur").toUpperCase(AppUiPrefs.locale(this))); over.setTextColor(LIME); over.setTextSize(10); over.setTypeface(null, Typeface.BOLD); over.setLetterSpacing(.10f); over.setGravity(Gravity.CENTER);
        header.addView(over);
        TextView title = new TextView(this);
        title.setText(AppUiPrefs.t(this,"Connexion / inscription")); title.setTextColor(TEXT); title.setTextSize(21); title.setTypeface(null, Typeface.BOLD); title.setGravity(Gravity.CENTER); title.setPadding(0,dp(4),0,0);
        header.addView(title);
        status = new TextView(this);
        status.setText(AppUiPrefs.t(this,"Crée ton compte sur le site puis l'application prendra automatiquement le relais."));
        status.setTextColor(MUTED); status.setTextSize(12); status.setGravity(Gravity.CENTER); status.setPadding(dp(8), dp(6), dp(8), 0);
        header.addView(status);
        TextView safe = new TextView(this); safe.setText(AppUiPrefs.t(this,"●  Connexion sécurisée au site Unité Paintball")); safe.setTextColor(LIME); safe.setTextSize(11); safe.setTypeface(null,Typeface.BOLD); safe.setGravity(Gravity.CENTER); safe.setPadding(dp(10),dp(6),dp(10),dp(6)); safe.setBackground(bg(Color.argb(16,157,255,53),999,Color.argb(70,32,229,243)));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.setMargins(0,dp(10),0,0);header.addView(safe,sp);
        root.addView(header);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        web = new WebView(this);
        web.setBackgroundColor(BG);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " UnitePaintball-Android/" + AppConfig.APP_VERSION);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        connectionRetry=new Button(this);connectionRetry.setText("Réessayer la connexion");connectionRetry.setAllCaps(false);connectionRetry.setTextColor(TEXT);connectionRetry.setBackground(bg(SURFACE,14,LIME));connectionRetry.setVisibility(android.view.View.GONE);connectionRetry.setOnClickListener(v->{pageFailed=false;connectionRetry.setVisibility(android.view.View.GONE);web.setVisibility(android.view.View.VISIBLE);openExistingSiteAuthentication();});root.addView(connectionRetry);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progress.setIndeterminate(true);
                status.setText(AppUiPrefs.t(AuthActivity.this,"Chargement de l'inscription / connexion…"));
            }

            @Override public void onPageFinished(WebView view, String url) {
                if(pageFailed)return;
                progress.setIndeterminate(false);
                CookieManager.getInstance().flush();
                status.setText(AppUiPrefs.t(AuthActivity.this,"Une fois ton compte créé ou connecté, l'application s'ouvrira automatiquement."));
                probeExistingSession();
            }

            @Override public void onReceivedHttpError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceResponse response){if(request.isForMainFrame()&&response.getStatusCode()>=500)showConnectionFailure();}
            @Override public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) showConnectionFailure();
            }
        });
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setPadding(dp(6),dp(6),dp(6),dp(6));shell.setBackground(bg(SURFACE,18,AppUiPrefs.stroke(this)));shell.addView(web,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,0,1);wp.setMargins(dp(8),dp(8),dp(8),dp(8));root.addView(shell,wp);
        setContentView(root);
        AppUiPrefs.applySafeArea(root);
    }

    private GradientDrawable bg(int fill,int radiusDp,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radiusDp));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}

    private boolean pageFailed=false;
    private Button connectionRetry;
    private void showConnectionFailure(){pageFailed=true;progress.setIndeterminate(false);status.setText("Connexion momentanément indisponible. Réessaie pour reprendre la connexion à ton compte.");web.setVisibility(android.view.View.GONE);connectionRetry.setVisibility(android.view.View.VISIBLE);}
    private void openExistingSiteAuthentication() {
        // /mon-espace est déjà protégé par l'authentification actuelle du site.
        // Si l'utilisateur n'a pas encore complété son compte, le layout du site
        // affiche automatiquement le formulaire RegistrationGate existant.
        web.loadUrl(AppConfig.BASE_URL + "/mon-espace");
    }

    private void probeExistingSession() {
        if (checking || completed) return;
        String cookies = CookieManager.getInstance().getCookie(AppConfig.BASE_URL);
        if (cookies == null || cookies.trim().isEmpty()) return;
        checking = true;
        io.execute(() -> {
            try {
                JSONObject platform = ApiClient.get("/api/platform", cookies);
                JSONObject profile = platform.optJSONObject("profile");
                JSONObject user = platform.optJSONObject("user");
                boolean registered = user != null && profile != null &&
                        !profile.isNull("onboardingCompletedAt") &&
                        !profile.optString("onboardingCompletedAt", "").isEmpty();
                if (registered) {
                    completed = true;
                    new SessionStore(this).saveToken(cookies);
                    BackgroundSyncScheduler.ensureScheduled(this);
                    BackgroundSyncScheduler.scheduleSoon(this);
                    runOnUiThread(() -> {
                        status.setText(AppUiPrefs.t(this,"Compte reconnu. Ouverture de l'application…"));
                        Intent main = new Intent(this, MainActivity.class);
                        if (!pendingTargetUrl.isEmpty()) main.putExtra("target_url", pendingTargetUrl);
                        if (!pendingNotificationCategory.isEmpty()) main.putExtra("notification_category", pendingNotificationCategory);
                        startActivity(main);
                        finish();
                    });
                }
            } catch (Exception ignored) {
                // Tant que le site n'a pas fini l'authentification ou l'inscription,
                // on laisse simplement la WebView affichée. Aucun message technique.
            } finally {
                checking = false;
            }
        });
    }

    private void handleBackNavigation() {
        if (web != null && web.canGoBack()) web.goBack();
        else finish();
    }

    private void registerSystemBackHandler() {
        if (Build.VERSION.SDK_INT >= 33 && systemBackCallback == null) {
            systemBackCallback = this::handleBackNavigation;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, systemBackCallback);
        }
    }

    private void unregisterSystemBackHandler() {
        if (Build.VERSION.SDK_INT >= 33 && systemBackCallback != null) {
            try { getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(systemBackCallback); } catch (Exception ignored) {}
            systemBackCallback = null;
        }
    }

    @Override public void onBackPressed() { handleBackNavigation(); }

    @Override protected void onDestroy() {
        unregisterSystemBackHandler();
        if (web != null) {
            web.stopLoading();
            web.destroy();
        }
        io.shutdownNow();
        super.onDestroy();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
