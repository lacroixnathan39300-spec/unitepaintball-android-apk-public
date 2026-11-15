package com.unitepaintball.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Outline;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;

public class LauncherActivity extends Activity {
    private static final long MIN_SPLASH_MS = 2200L;
    private static int ACCENT = NeonPalette.GREEN;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean soundEnabled = true;
    private boolean launched = false;
    private boolean resumed = false;
    private boolean preflightRunning = false;
    private final java.util.concurrent.ExecutorService preflightIo = java.util.concurrent.Executors.newSingleThreadExecutor();
    private TextView loadingText;
    private TextView percentText;
    private View progressFill;
    private TextView soundToggle;
    private long startedAt;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUiPrefs.migrateToSiteThemeSync(this);
        SiteThemeManager.applyCached(this);
        ACCENT = NeonPalette.GREEN;
        startedAt = System.currentTimeMillis();
        soundEnabled = getSharedPreferences("launcher_prefs", MODE_PRIVATE).getBoolean("sound_enabled", true);
        BackgroundSyncScheduler.ensureScheduled(this);
        configureWindow();
        setContentView(buildSplash());
        startMusic();
        startAnimations();
        scheduleLoadingMessages();
        handler.postDelayed(this::openApp, MIN_SPLASH_MS);
    }

    private void configureWindow() {
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private View buildSplash() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(NeonPalette.BG_DEEP);

        ParticleView particles = new ParticleView();
        root.addView(particles, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        PaintballFxView paintballFx = new PaintballFxView();
        root.addView(paintballFx, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LightningView lightning = new LightningView();
        root.addView(lightning, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        ImpactBurstView impacts = new ImpactBurstView();
        root.addView(impacts, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        View flash = new View(this);
        flash.setBackgroundColor(Color.WHITE);
        flash.setAlpha(0f);
        flash.setClickable(false);
        root.addView(flash,new FrameLayout.LayoutParams(-1,-1));

        View halo = new View(this);
        halo.setBackground(new android.graphics.drawable.GradientDrawable() {{
            setShape(OVAL);
            setGradientType(RADIAL_GRADIENT);
            setGradientRadius(dp(210));
            setColors(new int[]{Color.argb(70, 157, 255, 53), Color.argb(10, 32, 229, 243), Color.TRANSPARENT});
        }});
        FrameLayout.LayoutParams haloLp = new FrameLayout.LayoutParams(dp(430), dp(430), Gravity.CENTER);
        haloLp.topMargin = -dp(80);
        root.addView(halo, haloLp);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(28), dp(70), dp(28), dp(42));
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(content, contentLp);

        TextView eyebrow = label("UNITÉ PAINTBALL", 14, ACCENT, true);
        eyebrow.setLetterSpacing(0.23f);
        LinearLayout.LayoutParams eyebrowLp = new LinearLayout.LayoutParams(-2, -2);
        eyebrowLp.bottomMargin = dp(18);
        content.addView(eyebrow, eyebrowLp);

        ImageView logo = new ImageView(this);
        logo.setImageResource(com.unitepaintball.app.R.drawable.unite_paintball_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logo.setClipToOutline(true);
        android.graphics.drawable.GradientDrawable logoBg = new android.graphics.drawable.GradientDrawable();
        logoBg.setColor(Color.BLACK);
        logoBg.setCornerRadius(dp(28));
        logo.setBackground(logoBg);
        logo.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(28));
            }
        });
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(210), dp(210));
        logoLp.bottomMargin = dp(26);
        content.addView(logo, logoLp);

        TextView title = label("PRÊT À ENTRER SUR LE TERRAIN ?", 23, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.bottomMargin = dp(8);
        content.addView(title, titleLp);

        TextView subtitle = label("⚡ IMPACT • ACTION • PAINTBALL ⚡\nPont-du-Navoy • Jura", 15, NeonPalette.MUTED, true);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(-1, -2);
        subtitleLp.bottomMargin = dp(34);
        content.addView(subtitle, subtitleLp);

        loadingText = label("Préparation du terrain…", 16, Color.WHITE, true);
        loadingText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams loadLp = new LinearLayout.LayoutParams(-1, -2);
        loadLp.bottomMargin = dp(14);
        content.addView(loadingText, loadLp);

        FrameLayout progress = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable track = new android.graphics.drawable.GradientDrawable();
        track.setColor(NeonPalette.SURFACE_ALT);
        track.setCornerRadius(dp(8));
        progress.setBackground(track);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-1, dp(8));
        progressLp.setMargins(dp(18), 0, dp(18), dp(10));
        content.addView(progress, progressLp);

        progressFill = new View(this);
        android.graphics.drawable.GradientDrawable fill = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{NeonPalette.GREEN, NeonPalette.CYAN, NeonPalette.ORANGE});
        fill.setCornerRadius(dp(8));
        progressFill.setBackground(fill);
        FrameLayout.LayoutParams fillLp = new FrameLayout.LayoutParams(1, FrameLayout.LayoutParams.MATCH_PARENT);
        progress.addView(progressFill, fillLp);

        percentText = label("0 %", 13, NeonPalette.MUTED, true);
        percentText.setGravity(Gravity.CENTER);
        content.addView(percentText, new LinearLayout.LayoutParams(-1, -2));

        View spacer = new View(this);
        content.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        soundToggle = label("SON • ON", 12, ACCENT, true);
        soundToggle.setGravity(Gravity.CENTER);
        soundToggle.setPadding(dp(14), dp(10), dp(14), dp(10));
        android.graphics.drawable.GradientDrawable soundBg = new android.graphics.drawable.GradientDrawable();
        soundBg.setColor(Color.argb(120, 8, 23, 16));
        soundBg.setStroke(dp(1), Color.argb(150, 32, 229, 243));
        soundBg.setCornerRadius(dp(30));
        soundToggle.setBackground(soundBg);
        soundToggle.setText(soundEnabled ? "SON • ON" : "SON • OFF");
        soundToggle.setOnClickListener(v -> toggleSound());
        content.addView(soundToggle, new LinearLayout.LayoutParams(-2, -2));

        // Les effets doivent rester visiblement AU-DESSUS du contenu.
        // L'ancienne 2.2.4 les laissait derrière le logo et la barre, ce qui les rendait presque invisibles sur téléphone.
        paintballFx.bringToFront();
        lightning.bringToFront();
        impacts.bringToFront();
        flash.bringToFront();
        soundToggle.bringToFront();
        scheduleImpactFlashes(flash, lightning, impacts);

        animateLogoAndHalo(logo, halo, eyebrow);
        return root;
    }

    private void animateLogoAndHalo(View logo, View halo, View eyebrow) {
        logo.setScaleX(0.82f); logo.setScaleY(0.82f); logo.setAlpha(0f);
        eyebrow.setAlpha(0f);
        AnimatorSet entrance = new AnimatorSet();
        entrance.playTogether(
                ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.82f, 1f),
                ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.82f, 1f),
                ObjectAnimator.ofFloat(eyebrow, View.ALPHA, 0f, 1f));
        entrance.setDuration(800);
        entrance.setInterpolator(new DecelerateInterpolator());
        entrance.start();

        ObjectAnimator pulseX = ObjectAnimator.ofFloat(halo, View.SCALE_X, 0.88f, 1.12f);
        ObjectAnimator pulseY = ObjectAnimator.ofFloat(halo, View.SCALE_Y, 0.88f, 1.12f);
        ObjectAnimator pulseA = ObjectAnimator.ofFloat(halo, View.ALPHA, 0.45f, 1f);
        for (ObjectAnimator a : new ObjectAnimator[]{pulseX, pulseY, pulseA}) {
            a.setDuration(1700); a.setRepeatMode(ValueAnimator.REVERSE); a.setRepeatCount(ValueAnimator.INFINITE);
            a.start();
        }
    }

    private void scheduleImpactFlashes(View flash, LightningView lightning, ImpactBurstView impacts){
        // Séquence volontairement marquée : visible immédiatement sur un vrai téléphone.
        long[] times={180,430,720,1040,1390,1780,2050};
        for(int i=0;i<times.length;i++){
            final int idx=i;
            handler.postDelayed(()->{
                if(isFinishing())return;
                lightning.burst(idx);
                impacts.impact(idx);
                flash.animate().cancel();
                flash.setAlpha(idx%3==0?0.34f:0.20f);
                flash.animate().alpha(0f).setDuration(idx%3==0?210:150).start();
            },times[i]);
        }
    }

    private void startAnimations() {
        progressFill.post(() -> {
            View parent = (View) progressFill.getParent();
            int max = parent.getWidth();
            ValueAnimator animator = ValueAnimator.ofInt(1, max);
            animator.setDuration(MIN_SPLASH_MS - 180);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                int width = (int) a.getAnimatedValue();
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) progressFill.getLayoutParams();
                lp.width = Math.max(1, width);
                progressFill.setLayoutParams(lp);
                int pct = Math.min(100, Math.round((width * 100f) / Math.max(1, max)));
                percentText.setText(pct + " %");
            });
            animator.start();
        });
    }

    private void scheduleLoadingMessages() {
        handler.postDelayed(() -> setLoading("Chargement de votre espace…"), 320);
        handler.postDelayed(() -> setLoading("Lecture de la copie locale…"), 760);
        handler.postDelayed(() -> setLoading("Préparation de l'accueil…"), 1260);
        handler.postDelayed(() -> setLoading("⚡ C'est parti !"), 1840);
    }

    private void setLoading(String value) {
        if (!isFinishing() && loadingText != null) loadingText.setText(value);
    }

    private void startMusic() {
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        StartupAudioController.startOrContinue(this, soundEnabled);
    }

    private void toggleSound() {
        soundEnabled = !soundEnabled;
        getSharedPreferences("launcher_prefs", MODE_PRIVATE).edit().putBoolean("sound_enabled", soundEnabled).apply();
        StartupAudioController.setEnabled(soundEnabled);
        if (soundToggle != null) soundToggle.setText(soundEnabled ? "SON • ON" : "SON • OFF");
    }

    private void openApp() {
        if (launched || isFinishing()) return;
        if (!resumed) { handler.postDelayed(this::openApp, 250); return; }
        SessionStore s = new SessionStore(this);
        boolean authenticated = !s.token().isEmpty();
        if (!authenticated) {
            launched = true; StartupAudioController.release(); launchTarget(AuthActivity.class); return;
        }
        if (preflightRunning) return;
        preflightRunning = true;
        if (loadingText != null) loadingText.setText("Synchronisation du compte…");
        if (percentText != null) percentText.setText("Connexion à l’API Unité Paintball");
        preflightIo.execute(() -> {
            String error = null;
            try {
                String[] required = {"/api/platform","/api/avatar/soldier","/api/avatar","/api/gamification?scope=profile"};
                for (int i=0;i<required.length;i++) {
                    ApiClient.get(required[i], s.token());
                    final int done=i+1; runOnUiThread(() -> {
                        if (loadingText != null) loadingText.setText("Synchronisation obligatoire… "+done+"/4");
                        if (percentText != null) percentText.setText((done*25)+" %");
                    });
                }
                s.saveLastSuccessSync(System.currentTimeMillis());
            } catch (Exception e) { error=e.getMessage()==null?"Connexion impossible":e.getMessage(); }
            final String failure=error; runOnUiThread(() -> {
                preflightRunning=false;
                if (failure != null) {
                    if (loadingText != null) loadingText.setText("Synchronisation incomplète · nouvelle tentative…");
                    if (percentText != null) percentText.setText(failure);
                    handler.postDelayed(this::openApp,3000);
                    return;
                }
                launched=true; launchTarget(MainActivity.class);
            });
        });
    }

    private void launchTarget(Class<?> cls) {
        Intent target = new Intent(this, cls);
        Intent source = getIntent();
        if (source != null) {
            target.putExtra("target_url", source.getStringExtra("target_url"));
            target.putExtra("notification_category", source.getStringExtra("notification_category"));
        }
        startActivity(target);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void stopMusic() {
        StartupAudioController.release();
    }


    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (!launched) StartupAudioController.startOrContinue(this, soundEnabled);
    }

    @Override protected void onPause() {
        resumed = false;
        // Pendant la transition Launcher -> Main, la musique continue sans coupure.
        // Si l’utilisateur quitte réellement le splash, on la libère dans onDestroy si nécessaire.
        super.onPause();
    }

    @Override protected void onDestroy() {
        preflightIo.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        if (!launched) stopMusic();
        super.onDestroy();
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setTypeface(android.graphics.Typeface.DEFAULT, bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        return v;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private class PaintballFxView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random rnd=new Random(39300);
        private boolean initialized=false;
        private final float[] sx=new float[14],sy=new float[14],sr=new float[14];
        private final int[] sc=new int[14];
        PaintballFxView(){super(LauncherActivity.this);setAlpha(.95f);setClickable(false);}
        private void init(){int w=Math.max(1,getWidth()),h=Math.max(1,getHeight());int[] colors={NeonPalette.CYAN,NeonPalette.ORANGE,NeonPalette.GREEN};for(int i=0;i<sx.length;i++){sx[i]=rnd.nextFloat()*w;sy[i]=(0.08f+rnd.nextFloat()*.76f)*h;sr[i]=dp(8)+rnd.nextFloat()*dp(22);sc[i]=colors[i%colors.length];}initialized=true;}
        @Override protected void onDraw(Canvas c){super.onDraw(c);if(!initialized)init();for(int i=0;i<sx.length;i++){p.setColor(Color.argb(i%3==0?82:52,Color.red(sc[i]),Color.green(sc[i]),Color.blue(sc[i])));c.drawCircle(sx[i],sy[i],sr[i],p);for(int k=0;k<5;k++){float a=(float)(k*1.256+i*.7),d=sr[i]*(1.25f+k*.23f);c.drawCircle(sx[i]+(float)Math.cos(a)*d,sy[i]+(float)Math.sin(a)*d,Math.max(dp(1.2f),sr[i]*.13f),p);}}}
    }

    private class LightningView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random rnd=new Random();
        private long activeUntil=0;
        private int burstIndex=0;
        LightningView(){
            super(LauncherActivity.this);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            setClickable(false);
        }
        void burst(int index){
            burstIndex=index;
            activeUntil=System.currentTimeMillis()+430;
            invalidate();
        }
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            long now=System.currentTimeMillis();
            if(now>activeUntil)return;
            float life=Math.max(0f,Math.min(1f,(activeUntil-now)/430f));
            int alpha=(int)(255*(0.35f+0.65f*life));
            for(int bolt=0;bolt<4;bolt++){
                float base=(bolt==0?.08f:bolt==1?.92f:bolt==2?.20f:.80f);
                float x=base*getWidth();
                // Dépasse volontairement de l'écran : aucun arrêt visible en haut ou en bas.
                float startY=-getHeight()*0.08f;
                float endY=getHeight()*1.08f;
                float y=startY;
                android.graphics.Path path=new android.graphics.Path();
                path.moveTo(x,y);
                int steps=14;
                for(int i=1;i<=steps;i++){
                    x+=(rnd.nextFloat()-.5f)*dp(48);
                    y=startY+(endY-startY)*(i/(float)steps);
                    path.lineTo(x,y);
                }
                p.setStrokeWidth(dp(8f));
                p.setColor(Color.argb((int)(65*life),255,255,255));
                c.drawPath(path,p);
                p.setStrokeWidth(dp(3.2f));
                int col=(bolt+burstIndex)%2==0?NeonPalette.CYAN:NeonPalette.ORANGE;
                p.setColor(Color.argb(alpha,Color.red(col),Color.green(col),Color.blue(col)));
                c.drawPath(path,p);
                p.setStrokeWidth(dp(1.15f));
                p.setColor(Color.argb(Math.min(255,alpha+40),255,255,255));
                c.drawPath(path,p);
            }
            postInvalidateDelayed(24);
        }
    }

    private class ImpactBurstView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random rnd=new Random(20261002);
        private long activeUntil=0;
        private long started=0;
        private float cx,cy;
        private int color=ACCENT;
        private int seedIndex=0;
        ImpactBurstView(){super(LauncherActivity.this);setClickable(false);}
        void impact(int index){
            seedIndex=index;
            float[] xs={.14f,.82f,.22f,.76f,.12f,.88f,.50f};
            float[] ys={.22f,.30f,.68f,.62f,.46f,.76f,.18f};
            cx=getWidth()*xs[index%xs.length];
            cy=getHeight()*ys[index%ys.length];
            color=index%2==0?NeonPalette.CYAN:NeonPalette.ORANGE;
            started=System.currentTimeMillis();
            activeUntil=started+720;
            invalidate();
        }
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            long now=System.currentTimeMillis();
            if(now>activeUntil)return;
            float t=Math.min(1f,(now-started)/720f);
            float fade=1f-t;
            float radius=dp(22)+dp(92)*t;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(5f)*(0.4f+fade));
            p.setColor(Color.argb((int)(210*fade),Color.red(color),Color.green(color),Color.blue(color)));
            c.drawCircle(cx,cy,radius,p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb((int)(185*fade),Color.red(color),Color.green(color),Color.blue(color)));
            c.drawCircle(cx,cy,dp(15)+dp(20)*fade,p);
            for(int i=0;i<16;i++){
                double a=i*(Math.PI*2/16.0)+seedIndex*.37;
                float dist=dp(34)+dp(112)*t+(i%3)*dp(7);
                float rr=dp(2.8f)+(i%4)*dp(1.1f);
                c.drawCircle(cx+(float)Math.cos(a)*dist,cy+(float)Math.sin(a)*dist,rr,p);
            }
            postInvalidateDelayed(24);
        }
    }

    private class ParticleView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] x = new float[26];
        private final float[] y = new float[26];
        private final float[] r = new float[26];
        private final float[] speed = new float[26];
        private boolean ready;

        ParticleView() { super(LauncherActivity.this); paint.setColor(Color.argb(90, 157, 255, 53)); }

        private void init() {
            Random random = new Random(39);
            for (int i = 0; i < x.length; i++) {
                x[i] = random.nextFloat() * Math.max(1, getWidth());
                y[i] = random.nextFloat() * Math.max(1, getHeight());
                r[i] = dp(1) + random.nextFloat() * dp(2.2f);
                speed[i] = dp(0.25f) + random.nextFloat() * dp(0.7f);
            }
            ready = true;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!ready) init();
            paint.setShader(new RadialGradient(getWidth() * 0.5f, getHeight() * 0.36f,
                    Math.max(getWidth(), getHeight()) * 0.65f,
                    new int[]{Color.argb(105, 157, 255, 53), Color.argb(32, 255, 115, 35), Color.TRANSPARENT},
                    null, Shader.TileMode.CLAMP));
            canvas.drawCircle(getWidth() * .5f, getHeight() * .36f, Math.max(getWidth(), getHeight()) * .65f, paint);
            paint.setShader(null);
            for (int i = 0; i < x.length; i++) {
                paint.setColor(i % 5 == 0 ? Color.argb(110, 255, 116, 35) : Color.argb(95, 157, 255, 53));
                canvas.drawCircle(x[i], y[i], r[i], paint);
                y[i] -= speed[i];
                if (y[i] < -10) y[i] = getHeight() + 10;
            }
            postInvalidateDelayed(28);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
