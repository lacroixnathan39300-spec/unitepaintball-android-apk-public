package com.unitepaintball.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.widget.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import org.json.*;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.*;

/** Administration native des mêmes bons cadeaux que le site. Aucune mutation hors ligne. */
public final class GiftCertificateActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private LinearLayout root,body,tabs;
    private TextView status;
    private JSONArray gifts=new JSONArray();
    private boolean authorized,busy;
    private int tab;
    private JSONObject pendingCreate;
    private final Map<String,EditText> fields=new LinkedHashMap<>();
    private Spinner kind;
    private CheckBox equipment;
    private final String[] kinds={"free_players","three_plus_one","amount","percent","custom"};
    private JSONObject selected;
    private String scanInput="";
    private int generation;
    private android.content.SharedPreferences drafts;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);AppUiPrefs.applyWindow(this);
        drafts=getSharedPreferences("up_gift_admin",MODE_PRIVATE);
        try{String raw=drafts.getString("pending_create","");if(!raw.isEmpty())pendingCreate=new JSONObject(raw);}catch(Exception ignored){}
        root=column();root.setPadding(dp(18),dp(14),dp(18),dp(12));root.setBackgroundColor(AppUiPrefs.bg(this));
        root.addView(button("‹ Administration",()->{if(!busy)finish();}));
        root.addView(text("Bons cadeaux",28,true));
        root.addView(text("Unité Paintball · Création, partage et contrôle",14,false));
        status=text("Vérification de l’accès administrateur…",14,false);root.addView(status);
        tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);root.addView(tabs);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=column();scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);AppUiPrefs.applySafeArea(root);loadList();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView text(String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(bold?AppUiPrefs.text(this):AppUiPrefs.muted(this));t.setPadding(0,dp(6),0,dp(8));if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private Button button(String s,Runnable action){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(AppUiPrefs.primaryText(this));GradientDrawable bg=new GradientDrawable();bg.setColor(AppUiPrefs.primary(this));bg.setCornerRadius(dp(14));b.setBackground(bg);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(6),0,dp(8));b.setLayoutParams(p);b.setMinHeight(dp(48));b.setOnClickListener(v->{if(!busy)action.run();});return b;}
    private LinearLayout card(){LinearLayout v=column();v.setPadding(dp(16),dp(12),dp(16),dp(12));GradientDrawable bg=new GradientDrawable();bg.setColor(AppUiPrefs.card(this));bg.setCornerRadius(dp(20));bg.setStroke(dp(1),AppUiPrefs.stroke(this));v.setBackground(bg);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(12),0,dp(12));v.setLayoutParams(p);body.addView(v);return v;}
    private EditText field(LinearLayout into,String key,String label,String initial,int max){into.addView(text(label,14,true));EditText e=new EditText(this);e.setTextColor(AppUiPrefs.text(this));e.setHintTextColor(AppUiPrefs.muted(this));e.setTextSize(16);e.setText(initial);e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);into.addView(e,new LinearLayout.LayoutParams(-1,-2));if(key!=null)fields.put(key,e);return e;}
    private void showTabs(){tabs.removeAllViews();String[] names={"Créer","Mes bons","Contrôler"};for(int i=0;i<names.length;i++){final int n=i;Button b=button(names[i],()->{tab=n;selected=null;if(n==1)loadList();else render();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(2),dp(6),dp(2),dp(6));b.setLayoutParams(p);b.setAlpha(tab==i?1:.65f);tabs.addView(b);}}
    private void render(){if(!authorized)return;showTabs();body.removeAllViews();fields.clear();if(selected!=null){showGift(selected);return;}if(tab==0)createForm();else if(tab==1)listForm();else controlForm();}
    private interface Work{JSONObject run()throws Exception;}
    private interface Done{void run(JSONObject j)throws Exception;}
    private void request(String label,Work task,Done done){if(busy)return;busy=true;status.setText(label);int ticket=++generation;io.execute(()->{JSONObject result=null;Exception error=null;try{result=task.run();}catch(Exception e){error=e;}JSONObject r=result;Exception problem=error;runOnUiThread(()->{if(isFinishing()||isDestroyed()||ticket!=generation)return;busy=false;if(problem!=null){status.setText("Échec : "+problem.getMessage());return;}try{done.run(r);}catch(Exception e){status.setText("Erreur : "+e.getMessage());}});});}
    private String token(){return new SessionStore(this).token();}
    private void loadList(){request("Chargement des bons…",()->ApiClient.get("/api/bons-cadeaux",token()),j->{gifts=j.optJSONArray("gifts");if(gifts==null)throw new IllegalStateException("Liste absente de la réponse serveur.");authorized=true;status.setText("Bons synchronisés avec le site");render();String code=getIntent().getStringExtra("giftCode");if(code!=null){getIntent().removeExtra("giftCode");tab=2;lookup(code);}});if(!authorized){body.removeAllViews();body.addView(button("Réessayer",this::loadList));}}
    private JSONObject draft(){return pendingCreate==null?new JSONObject():pendingCreate;}
    private void createForm(){
        LinearLayout c=card();c.addView(text("Créer un bon personnalisé",21,true));
        if(pendingCreate!=null)c.addView(text("Création à confirmer : les mêmes données seront renvoyées pour éviter un doublon. Consulte aussi Mes bons.",14,false));
        JSONObject d=draft();field(c,"title","Titre *",d.optString("title","Une partie offerte"),120);
        field(c,"benefit","Cadeau / avantage *",d.optString("benefit","Une partie de paintball pour 1 personne"),1200);
        field(c,"recipient","Destinataire",d.optString("recipient"),120);field(c,"sender","Offert par",d.optString("sender"),120);
        field(c,"message","Message personnel",d.optString("message"),600);field(c,"conditions","Conditions d’utilisation",d.optString("conditions","Sur réservation, selon disponibilités. Bon à usage unique."),1200);
        field(c,"expires_at","Valable jusqu’au (AAAA-MM-JJ, vide = sans expiration)",d.optString("expires_at"),10);
        effectForm(c,d.optJSONObject("effect"));
        c.addView(text("Le QR ouvre le bon sur le site et permet au bénéficiaire de réserver avec son avantage. Un scan de contrôle ne consomme pas le bon.",14,false));
        if(pendingCreate!=null){setEditable(c,false);}
        c.addView(button(pendingCreate==null?"Créer le bon et son QR":"Confirmer cette création",this::createGift));
        if(pendingCreate!=null)c.addView(button("Nouveau brouillon",()->new AlertDialog.Builder(this).setTitle("Vérifier avant de recommencer").setMessage("Le bon précédent a peut-être été créé malgré l’erreur réseau. Consulte Mes bons pour éviter de créer deux cadeaux.").setNegativeButton("Retour",null).setPositiveButton("Nouveau brouillon",(a,b)->{clearPending();render();}).show()));
    }
    private void setEditable(LinearLayout c,boolean value){for(int i=0;i<c.getChildCount();i++){View v=c.getChildAt(i);if(v instanceof EditText||v instanceof Spinner||v instanceof CheckBox)v.setEnabled(value);}}
    private void effectForm(LinearLayout c,JSONObject effect){JSONObject e=effect==null?new JSONObject():effect;c.addView(text("Avantage appliqué à la réservation",16,true));kind=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Personne(s) offerte(s)","3 parties achetées, 1 offerte (4 personnes)","Réduction en euros","Réduction en %","Cadeau personnalisé (contrôle manuel)"});kind.setAdapter(adapter);int index=Arrays.asList(kinds).indexOf(e.optString("kind","free_players"));kind.setSelection(Math.max(0,index));c.addView(kind);EditText value=field(c,"value","Valeur : personnes, euros ou pourcentage",String.valueOf(e.optInt("value",1)),6);value.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);equipment=new CheckBox(this);equipment.setText("Équipement inclus pour la personne offerte");equipment.setTextColor(AppUiPrefs.text(this));equipment.setChecked(e.optBoolean("equipment"));c.addView(equipment);field(c,"offerCode","Code de formule concernée (facultatif)",e.optString("offerCode"),80);c.addView(text("Sans code de formule, toutes les formules compatibles sont acceptées. L’offre 3 + 1 nécessite une réservation de 4 personnes minimum.",13,false));}
    private JSONObject effect()throws Exception{String k=kinds[kind.getSelectedItemPosition()];int value="custom".equals(k)||"three_plus_one".equals(k)?0:Integer.parseInt(fields.get("value").getText().toString().trim());GiftCertificatePolicy.validateEffect(k,value);return new JSONObject().put("kind",k).put("value",value).put("equipment",equipment.isChecked()).put("offerCode",fields.get("offerCode").getText().toString().trim());}
    private void createGift(){try{if(pendingCreate==null){JSONObject p=new JSONObject().put("action","create").put("requestId",UUID.randomUUID().toString());for(String k:new String[]{"title","benefit","recipient","sender","message","conditions","expires_at"})p.put(k,fields.get(k).getText().toString().trim());if(p.getString("title").isEmpty()||p.getString("benefit").isEmpty())throw new IllegalArgumentException("Le titre et le cadeau sont obligatoires.");GiftCertificatePolicy.validateDate(p.getString("expires_at"));p.put("effect",effect());pendingCreate=p;drafts.edit().putString("pending_create",p.toString()).commit();render();}JSONObject payload=new JSONObject(pendingCreate.toString());request("Création du bon…",()->ApiClient.post("/api/bons-cadeaux",token(),payload),j->{selected=j.getJSONObject("gift");clearPending();status.setText("Bon créé et enregistré sur le site");render();});}catch(Exception e){status.setText(e.getMessage());}}
    private void clearPending(){pendingCreate=null;drafts.edit().remove("pending_create").commit();}
    private void listForm(){LinearLayout c=card();c.addView(text("Mes bons cadeaux",21,true));EditText search=field(c,null,"Rechercher (titre, code, destinataire)","",120);LinearLayout results=column();c.addView(results);Runnable filter=()->{results.removeAllViews();String q=search.getText().toString().toLowerCase(Locale.ROOT);int count=0;for(int i=0;i<gifts.length();i++){JSONObject g=gifts.optJSONObject(i);if(g==null)continue;String s=g.optString("title")+" "+g.optString("code")+" "+g.optString("recipient");if(!s.toLowerCase(Locale.ROOT).contains(q))continue;count++;results.addView(button(g.optString("title")+"\n"+stateLabel(g)+" · "+g.optString("recipient"),()->{selected=g;render();}));}if(count==0)results.addView(text("Aucun bon correspondant.",15,false));};search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int count,int after){}public void onTextChanged(CharSequence s,int a,int before,int count){filter.run();}public void afterTextChanged(android.text.Editable e){}});filter.run();c.addView(button("Actualiser",this::loadList));}
    private void controlForm(){LinearLayout c=card();c.addView(text("Contrôler un bon",21,true));c.addView(text("Scanne le QR ou colle le code. La consultation n’utilise pas le bon.",15,false));EditText code=field(c,null,"Code ou lien du bon",scanInput,500);code.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);c.addView(button("Consulter",()->lookup(code.getText().toString())));c.addView(button("Scanner le QR",()->{IntentIntegrator scan=new IntentIntegrator(this);scan.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);scan.setPrompt("Scanner un bon cadeau Unité Paintball");scan.setBeepEnabled(true);scan.setOrientationLocked(true);scan.initiateScan();}));}
    private void lookup(String raw){String code=GiftCertificatePolicy.code(raw,AppConfig.BASE_URL);if(code.isEmpty()){status.setText("Code ou lien de bon cadeau invalide.");return;}scanInput=code;request("Vérification du bon…",()->ApiClient.get("/api/bons-cadeaux?code="+URLEncoder.encode(code,"UTF-8"),token()),j->{selected=j;status.setText("Bon vérifié · aucune utilisation enregistrée par ce scan");render();});}
    private String state(JSONObject g){String s=g.optString("state",g.optString("status"));String expiry=g.optString("expires_at","");if("active".equals(s)&&!expiry.isEmpty()&&expiry.compareTo(java.time.LocalDate.now(java.time.ZoneId.of("Europe/Paris")).toString())<0)return "expired";return s;}
    private String stateLabel(JSONObject g){switch(state(g)){case "active":return "Disponible";case "reserved":return "Réservé";case "redeemed":return "Utilisé";case "cancelled":return "Annulé";case "expired":return "Expiré";default:return "À vérifier";}}
    private void showGift(JSONObject g){
        LinearLayout c=card();c.addView(button("‹ Retour",()->{selected=null;render();}));c.addView(text(g.optString("title"),25,true));c.addView(text(stateLabel(g),18,true));c.addView(text(g.optString("benefit"),18,false));
        for(String[] f:new String[][]{{"recipient","Pour"},{"sender","Offert par"},{"message","Message"},{"conditions","Conditions"},{"expires_at","Expiration"}}){String v=g.optString(f[0],"");if(!v.isEmpty())c.addView(text(f[1]+" : "+v,15,false));}
        String code=g.optString("code"),link=AppConfig.BASE_URL+"/bon-cadeau?code="+code;c.addView(text(code,16,true));
        try{Map<EncodeHintType,Object> hints=new EnumMap<>(EncodeHintType.class);hints.put(EncodeHintType.MARGIN,4);BitMatrix matrix=new MultiFormatWriter().encode(link,BarcodeFormat.QR_CODE,640,640,hints);Bitmap bmp=Bitmap.createBitmap(640,640,Bitmap.Config.RGB_565);int[] pixels=new int[640*640];for(int y=0;y<640;y++)for(int x=0;x<640;x++)pixels[y*640+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;bmp.setPixels(pixels,0,640,0,0,640,640);ImageView qr=new ImageView(this);qr.setImageBitmap(bmp);qr.setContentDescription("QR code du bon cadeau "+code);qr.setAdjustViewBounds(true);c.addView(qr,new LinearLayout.LayoutParams(-1,dp(280)));}catch(Exception e){c.addView(text("QR indisponible : "+e.getMessage(),14,false));}
        c.addView(button("Partager le bon",()->{Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,g.optString("title")+"\n"+g.optString("benefit")+"\n"+link);startActivity(Intent.createChooser(i,"Partager le bon cadeau"));}));
        c.addView(button("Copier le lien",()->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Bon cadeau",link));status.setText("Lien copié");}));
        String s=state(g);if("active".equals(s)||"reserved".equals(s)){
            CheckBox confirm=new CheckBox(this);confirm.setText("J’ai remis le cadeau et vérifié toutes les conditions.");confirm.setTextColor(AppUiPrefs.text(this));c.addView(confirm);c.addView(button("Valider l’utilisation unique",()->{if(!confirm.isChecked()){status.setText("Confirme la remise et les conditions avant de valider.");return;}new AlertDialog.Builder(this).setTitle("Utiliser ce bon ?").setMessage("Cette validation est définitive. Le bon ne pourra plus être utilisé.").setNegativeButton("Retour",null).setPositiveButton("Valider",(a,b)->mutate("redeem",code,"")).show();}));
        }
        if("active".equals(s)){c.addView(button("Modifier l’avantage de réservation",()->editEffect(g)));c.addView(button("Annuler ce bon",()->{EditText reason=new EditText(this);reason.setHint("Motif de l’annulation");new AlertDialog.Builder(this).setTitle("Annuler définitivement ce bon ?").setView(reason).setNegativeButton("Retour",null).setPositiveButton("Annuler le bon",(a,b)->mutate("cancel",code,reason.getText().toString())).show();}));}
        c.addView(button("Actualiser ce bon",()->lookup(code)));
    }
    private void editEffect(JSONObject g){body.removeAllViews();fields.clear();LinearLayout c=card();c.addView(text("Modifier l’avantage",22,true));effectForm(c,g.optJSONObject("effect"));c.addView(button("Enregistrer",()->{try{JSONObject p=new JSONObject().put("action","configure").put("code",g.getString("code")).put("effect",effect());request("Enregistrement…",()->ApiClient.post("/api/bons-cadeaux",token(),p),j->{status.setText("Avantage enregistré");lookup(g.getString("code"));});}catch(Exception e){status.setText(e.getMessage());}}));c.addView(button("Retour",this::render));}
    private void mutate(String action,String code,String reason){try{JSONObject p=new JSONObject().put("action",action).put("code",code).put("confirm",true).put("reason",reason);request("Validation sur le serveur…",()->ApiClient.post("/api/bons-cadeaux",token(),p),j->{status.setText("Action enregistrée");lookup(code);});}catch(Exception e){status.setText(e.getMessage());}}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){IntentResult r=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);if(r!=null){if(r.getContents()!=null){tab=2;lookup(r.getContents());}return;}super.onActivityResult(requestCode,resultCode,data);}
    @Override protected void onDestroy(){generation++;io.shutdownNow();super.onDestroy();}
}
