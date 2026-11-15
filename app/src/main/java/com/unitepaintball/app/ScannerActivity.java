package com.unitepaintball.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import org.json.JSONObject;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScannerActivity extends Activity {
    private static int BG=NeonPalette.BG, SURFACE=NeonPalette.SURFACE, PRIMARY=NeonPalette.GREEN, PRIMARY_TEXT=NeonPalette.PRIMARY_TEXT, TEXT=NeonPalette.TEXT, MUTED=NeonPalette.MUTED;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SessionStore store;
    private LinearLayout root;
    private TextView status;
    private String scannedToken = "";
    private JSONObject preview;
    private boolean rewardMode=false;
    private int scanGeneration=0;
    private boolean rewardSubmitting=false;
    private CheckBox paidGames;
    private LinearLayout previewCard;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BG=AppUiPrefs.bg(this);SURFACE=AppUiPrefs.card(this);PRIMARY=AppUiPrefs.primary(this);PRIMARY_TEXT=AppUiPrefs.primaryText(this);TEXT=AppUiPrefs.text(this);MUTED=AppUiPrefs.muted(this);
        AppUiPrefs.applyWindow(this);
        store = new SessionStore(this);
        buildUi();
        startScan();
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(22),dp(18),dp(28));
        root.setGravity(Gravity.TOP);
        root.setBackgroundColor(BG);
        TextView over = new TextView(this); over.setText("UNITÉ PAINTBALL"); over.setTextColor(PRIMARY); over.setTextSize(11); over.setTypeface(null,Typeface.BOLD); over.setLetterSpacing(.08f); root.addView(over);
        TextView title = new TextView(this);
        title.setText(AppUiPrefs.t(this,"Scanner QR joueur ou gain")); title.setTextColor(TEXT); title.setTypeface(null,Typeface.BOLD); title.setTextSize(28); title.setPadding(0,dp(8),0,0); root.addView(title);
        status = new TextView(this); status.setText(AppUiPrefs.t(this,"Prêt à scanner…")); status.setTextColor(MUTED); status.setTextSize(15); status.setPadding(0,dp(14),0,dp(16)); root.addView(status);
        Button scan = siteButton("Scanner un QR"); scan.setOnClickListener(v->startScan()); root.addView(scan);
        Button validate = siteButton("Valider la présence"); validate.setEnabled(false); validate.setTag("validate"); validate.setOnClickListener(v->validateCheckin()); root.addView(validate);
        setContentView(root);
        AppUiPrefs.applySafeArea(root);
    }

    private Button validateButton(){
        for(int i=0;i<root.getChildCount();i++) if(root.getChildAt(i) instanceof Button && "validate".equals(root.getChildAt(i).getTag())) return (Button)root.getChildAt(i);
        return null;
    }

    private void startScan(){
        if(rewardSubmitting)return;scanGeneration++;
        preview=null;scannedToken="";rewardMode=false;Button resetButton=validateButton();if(resetButton!=null)resetButton.setText("Valider la présence");if(paidGames!=null){root.removeView(paidGames);paidGames=null;}Button vb=validateButton();if(vb!=null)vb.setEnabled(false);if(previewCard!=null&&previewCard.getParent()!=null)root.removeView(previewCard);previewCard=null;status.setText(AppUiPrefs.t(this,"Prêt à scanner…"));
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt(AppUiPrefs.t(this,"Scanne le QR Unité Paintball"));
        integrator.setBeepEnabled(true);
        integrator.setOrientationLocked(true);
        integrator.initiateScan();
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,android.content.Intent data){
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode,resultCode,data);
        if(result!=null){
            if(result.getContents()!=null){ scannedToken=result.getContents().trim(); if(scannedToken.startsWith(AppConfig.BASE_URL+"/bon-cadeau?")){String giftCode=GiftCertificatePolicy.code(scannedToken,AppConfig.BASE_URL);if(!giftCode.isEmpty()){startActivity(new android.content.Intent(this,GiftCertificateActivity.class).putExtra("giftCode",giftCode));finish();return;}} if(scannedToken.matches("(?i)UP-[A-F0-9]{12,32}")){rewardMode=true;previewReward();}else previewCheckin(); }
            else status.setText(AppUiPrefs.t(this,"Scan annulé."));
            return;
        }
        super.onActivityResult(requestCode,resultCode,data);
    }

    private void previewCheckin(){
        status.setText(AppUiPrefs.t(this,"Vérification du joueur…"));
        io.execute(()->{
            try{
                JSONObject r=ApiClient.post("/api/checkin",store.token(),new JSONObject().put("action","preview").put("token",scannedToken));
                preview=r;
                runOnUiThread(()->showPreview(r));
            }catch(Exception e){runOnUiThread(()->{status.setText("Erreur : "+e.getMessage()); Button b=validateButton(); if(b!=null)b.setEnabled(false);});}
        });
    }

    private void showPreview(JSONObject r){
        JSONObject p=r.optJSONObject("participant"), b=r.optJSONObject("booking");
        if(p==null||b==null){status.setText(AppUiPrefs.t(this,"QR non reconnu."));return;}
        status.setText(AppUiPrefs.t(this,"QR reconnu · vérifie les informations avant validation."));
        if(previewCard!=null&&previewCard.getParent()!=null)root.removeView(previewCard);
        LinearLayout card=new LinearLayout(this);previewCard=card;card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(16),dp(16),dp(16));GradientDrawable g=new GradientDrawable();g.setColor(SURFACE);g.setCornerRadius(dp(16));g.setStroke(dp(1),AppUiPrefs.stroke(this));card.setBackground(g);
        TextView badge=new TextView(this);badge.setText(AppUiPrefs.t(this,r.optBoolean("alreadyValidated",false)?"DÉJÀ VALIDÉ":"JOUEUR TROUVÉ"));badge.setTextColor(PRIMARY_TEXT);badge.setTypeface(null,Typeface.BOLD);badge.setTextSize(10);badge.setGravity(Gravity.CENTER);GradientDrawable bg=new GradientDrawable();bg.setColor(PRIMARY);bg.setCornerRadius(dp(99));badge.setBackground(bg);badge.setPadding(dp(10),dp(5),dp(10),dp(5));card.addView(badge,new LinearLayout.LayoutParams(-2,-2));
        TextView name=new TextView(this);name.setText(p.optString("name","Joueur"));name.setTextColor(TEXT);name.setTextSize(22);name.setTypeface(null,Typeface.BOLD);name.setPadding(0,dp(12),0,dp(6));card.addView(name);
        TextView details=new TextView(this);details.setText(AppUiPrefs.t(this,"Pseudo")+" : "+p.optString("nickname")+"\n"+AppUiPrefs.t(this,"Réservation")+" : "+b.optString("reference")+"\nDate : "+b.optString("date")+" · "+b.optString("time")+"\n"+AppUiPrefs.t(this,"Groupe")+" : "+r.optString("group")+"\n"+AppUiPrefs.t(this,"Prix attendu")+" : "+r.optInt("expectedPrice",0)+" €");details.setTextColor(MUTED);details.setTextSize(14);details.setLineSpacing(0,1.15f);card.addView(details);
        root.addView(card,root.getChildCount()-1,new LinearLayout.LayoutParams(-1,-2));
        Button vb=validateButton(); if(vb!=null) vb.setEnabled(!r.optBoolean("alreadyValidated",false));
    }

    private void validateCheckin(){
        if(scannedToken.isEmpty()||preview==null)return;
        if(rewardMode){confirmReward();return;}
        JSONObject p=preview.optJSONObject("participant"), b=preview.optJSONObject("booking");
        new AlertDialog.Builder(this)
                .setTitle(AppUiPrefs.t(this,"Valider la présence ?"))
                .setMessage((p==null?"Joueur":p.optString("name"))+" · "+(b==null?"":b.optString("reference")))
                .setNegativeButton(AppUiPrefs.t(this,"Annuler"),null)
                .setPositiveButton(AppUiPrefs.t(this,"Valider"),(d,w)->doValidate())
                .show();
    }

    private void doValidate(){
        status.setText("Validation…"); Button vb=validateButton(); if(vb!=null)vb.setEnabled(false);
        io.execute(()->{
            try{
                int expected=preview.optInt("expectedPrice",0);
                JSONObject body=new JSONObject().put("action","validate").put("token",scannedToken)
                        .put("idempotencyKey","android-"+UUID.randomUUID())
                        .put("finalPrice",expected).put("discount",0).put("paymentMethod","cash").put("paintballsSold",0).put("note","Validation depuis l'application Android");
                JSONObject r=ApiClient.post("/api/checkin",store.token(),body);
                runOnUiThread(()->{boolean dup=r.optBoolean("duplicate",false);status.setText(dup?"Présence déjà validée — aucun doublon créé.":"Présence validée avec succès.");if(vb!=null)vb.setEnabled(false);Toast.makeText(this,dup?"Déjà validé":"Check-in enregistré",Toast.LENGTH_LONG).show();});
            }catch(Exception e){runOnUiThread(()->{status.setText("Erreur : "+e.getMessage()); if(vb!=null)vb.setEnabled(true);});}
        });
    }

    private void previewReward(){final int ticket=scanGeneration;final String code=scannedToken;status.setText("Vérification du gain…");io.execute(()->{try{JSONObject r=ApiClient.post("/api/admin-gamification",store.token(),new JSONObject().put("action","preview_reward").put("validationCode",code));runOnUiThread(()->{if(ticket!=scanGeneration||isFinishing())return;preview=r;JSONObject reward=r.optJSONObject("reward");status.setText((reward==null?"Gain":reward.optString("name"))+"\n"+r.optString("playerName")+"\n"+(r.optBoolean("available")?"Disponible · confirmer uniquement après remise du lot":"Déjà utilisé, expiré ou indisponible"));Button b=validateButton();if(b!=null){b.setText("Confirmer la remise du gain");b.setEnabled(r.optBoolean("available"));}if(r.optBoolean("requiresThreePaidGames")){paidGames=new CheckBox(this);paidGames.setText("J’ai vérifié les 3 parties payées pour ce bon");paidGames.setTextColor(TEXT);root.addView(paidGames);}});}catch(Exception e){runOnUiThread(()->{if(ticket==scanGeneration&&!isFinishing())status.setText("Vérification impossible : "+e.getMessage());});}});}
    private void confirmReward(){if(rewardSubmitting||preview==null)return;if(paidGames!=null&&!paidGames.isChecked()){Toast.makeText(this,"Vérifie les 3 parties payées avant validation.",Toast.LENGTH_LONG).show();return;}JSONObject reward=preview.optJSONObject("reward");new AlertDialog.Builder(this).setTitle("Remettre ce gain une seule fois ?").setMessage((reward==null?"Gain":reward.optString("name"))+" · "+preview.optString("playerName")+"\nLe QR sera définitivement utilisé.").setNegativeButton("Annuler",null).setPositiveButton("Confirmer la remise",(d,w)->{final String code=scannedToken;final boolean paid=paidGames!=null&&paidGames.isChecked();rewardSubmitting=true;Button b=validateButton();if(b!=null)b.setEnabled(false);io.execute(()->{try{ApiClient.post("/api/admin-gamification",store.token(),new JSONObject().put("action","redeem_reward").put("validationCode",code).put("confirm",true).put("threePaidGamesConfirmed",paid));runOnUiThread(()->{rewardSubmitting=false;preview=null;status.setText("Gain remis · QR définitivement utilisé.");});}catch(Exception e){runOnUiThread(()->{rewardSubmitting=false;preview=null;status.setText("Remise non confirmée : "+e.getMessage()+"\nScanne à nouveau pour vérifier le statut serveur avant toute nouvelle remise.");});}});}).show();}

    private Button siteButton(String label){Button b=new Button(this);b.setText(AppUiPrefs.t(this,label));b.setAllCaps(false);b.setTextColor(PRIMARY_TEXT);b.setTypeface(null,Typeface.BOLD);b.setTextSize(14);GradientDrawable g=new GradientDrawable();g.setColor(PRIMARY);g.setCornerRadius(dp(12));b.setBackground(g);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));lp.setMargins(0,dp(6),0,dp(8));b.setLayoutParams(lp);return b;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){super.onDestroy();io.shutdownNow();}
}
