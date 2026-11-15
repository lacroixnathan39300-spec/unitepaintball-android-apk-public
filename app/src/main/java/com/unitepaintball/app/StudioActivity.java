package com.unitepaintball.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.net.Uri;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Studio natif Unité Paintball : éditeur visuel réservé à l'administration. */
public class StudioActivity extends Activity {
    private static int BG = NeonPalette.BG;
    private static int CARD = NeonPalette.SURFACE;
    private static int LIME = NeonPalette.GREEN;
    private static int PRIMARY_TEXT = NeonPalette.PRIMARY_TEXT;
    private static int ORANGE = NeonPalette.CYAN;
    private static int TEXT = NeonPalette.TEXT;
    private static int MUTED = NeonPalette.MUTED;
    private StudioCanvas canvas;
    private TextView modeLabel;
    private TextView inspectorTitle;
    private TextView inspectorBody;
    private LinearLayout inspectorActions;
    private Button targetButton;
    private Button environmentButton;
    private String mode = "Site complet";
    private String target = "Les deux";
    private String environment = "Brouillon";
    private String registryVersion = "";
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SessionStore store;
    private String serverPageId = "";
    private int serverRevision = 0;
    private static final String WORKSPACE_SLUG = "android-studio-workspace";
    private static final String[] STUDIO_MODES = {"Site complet","Automatisations","Gamification","Gameplay Arena","Pages","API / Actions","Menus","Formulaires","Scénarios","Tarifs","Notifications","Live Game","Tournois","Variables","Historique"};
    private static final String SITE_ROUTE_MARKER = "__UP_SITE_ROUTE__";
    private static final String SITE_MODULE_MARKER = "__UP_SITE_MODULE__";
    private static final String API_ACTION_MARKER = "__UP_API_ACTION__";
    private JSONObject registryCache;
    private static final String[] COMPLETE_SITE_ROUTES = {
        "/",
        "/adhesions",
        "/aide",
        "/ajouter-photo",
        "/amis",
        "/assistant",
        "/attente/[token]",
        "/briefing/[token]",
        "/charte",
        "/communication",
        "/confirmation/[token]",
        "/dossier-mineur/[token]",
        "/evenements",
        "/faq",
        "/galerie",
        "/galerie-privee",
        "/groupes",
        "/informations",
        "/informations-legales",
        "/installer",
        "/invitation/[token]",
        "/jeux",
        "/langues",
        "/mes-qr",
        "/messages-app",
        "/mon-espace",
        "/mon-espace/avant-de-venir",
        "/mon-espace/equipe",
        "/mon-espace/notifications",
        "/mon-espace/paiements",
        "/mon-espace/paiements/recu/[id]",
        "/mon-espace/prochaine-partie",
        "/mon-espace/profil",
        "/mon-espace/tournois",
        "/notifications",
        "/parametres",
        "/parties",
        "/parties/ecran",
        "/plateforme",
        "/profil",
        "/qr",
        "/reservations",
        "/securite",
        "/studio-p/[slug]",
        "/tarifs",
        "/admin",
        "/admin/a-valider",
        "/admin/absences",
        "/admin/adhesions",
        "/admin/administrateurs",
        "/admin/apparence",
        "/admin/audit",
        "/admin/automatisations",
        "/admin/commercial",
        "/admin/communications",
        "/admin/comptabilite",
        "/admin/comptabilite/fonds-a-reverser",
        "/admin/confidentialite",
        "/admin/confirmations",
        "/admin/dossiers-mineurs",
        "/admin/editeur",
        "/admin/editeur/pages-libres",
        "/admin/editeur/apercu/[slug]",
        "/admin/editeur/partage",
        "/admin/editeur/sauvegardes",
        "/admin/fiabilite",
        "/admin/galerie",
        "/admin/gestion",
        "/admin/grades-tarifs",
        "/admin/groupes",
        "/admin/historique",
        "/admin/informations",
        "/admin/joueurs",
        "/admin/modifications",
        "/admin/notifications",
        "/admin/nouveaux-arrivants",
        "/admin/nouvelles-reservations",
        "/admin/operations",
        "/admin/outils",
        "/admin/parametres",
        "/admin/parametres/espace-joueur",
        "/admin/participations",
        "/admin/partie-du-jour",
        "/admin/parties",
        "/admin/points",
        "/admin/publications",
        "/admin/recu/[id]",
        "/admin/reservations",
        "/admin/securite",
        "/admin/sessions",
        "/admin/statistiques",
        "/admin/stocks",
        "/admin/support",
        "/admin/tournois"
    };
    private final Map<String,Snapshot> modeStates = new LinkedHashMap<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        BG=AppUiPrefs.bg(this);CARD=AppUiPrefs.card(this);LIME=AppUiPrefs.primary(this);PRIMARY_TEXT=AppUiPrefs.primaryText(this);ORANGE=AppUiPrefs.orange(this);TEXT=AppUiPrefs.text(this);MUTED=AppUiPrefs.muted(this);
        AppUiPrefs.applyWindow(this);
        store = new SessionStore(this);
        build();
        if(canvas!=null){
            modeStates.clear();
            ensureComprehensiveModeStates();
            Snapshot initial=modeStates.get(mode);
            if(initial!=null)canvas.restore(initial);
            canvas.selected=-1; updateInspector(-1); refreshModeLabel();
        }
        loadServerWorkspace();
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12),dp(10),dp(12),dp(10));
        Button back = ghost("‹"); back.setTextSize(30); back.setOnClickListener(v->finish());
        top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL); copy.setPadding(dp(8),0,0,0);
        TextView over = text("STUDIO · SUPER ADMIN",10,true,LIME); over.setLetterSpacing(.08f); copy.addView(over);
        copy.addView(text("Éditeur visuel",25,true,TEXT));
        top.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        Button save = primary("Enregistrer"); save.setOnClickListener(v->saveServerWorkspace(false));
        top.addView(save,new LinearLayout.LayoutParams(dp(110),dp(46)));
        root.addView(top);

        HorizontalScrollView tabs = new HorizontalScrollView(this); tabs.setHorizontalScrollBarEnabled(false);
        LinearLayout tabRow = new LinearLayout(this); tabRow.setPadding(dp(10),0,dp(10),dp(8));
        for(String n:STUDIO_MODES){ Button b=chip(n); b.setOnClickListener(v->switchStudioMode(n)); tabRow.addView(b); }
        tabs.addView(tabRow); root.addView(tabs);

        LinearLayout status = new LinearLayout(this); status.setGravity(Gravity.CENTER_VERTICAL); status.setPadding(dp(14),dp(6),dp(14),dp(8));
        modeLabel=text("Site complet · Les deux · Brouillon",12,true,TEXT); status.addView(modeLabel,new LinearLayout.LayoutParams(0,-2,1));
        Button sitePreview=chip("🌐 Site complet"); sitePreview.setOnClickListener(v->showVisualSiteEditor("/")); status.addView(sitePreview,new LinearLayout.LayoutParams(-2,dp(38)));
        targetButton=chip("Cible : "+target); targetButton.setOnClickListener(v->{ target="Application".equals(target)?"Site":"Site".equals(target)?"Les deux":"Application"; refreshModeLabel();}); status.addView(targetButton,new LinearLayout.LayoutParams(-2,dp(38)));
        root.addView(status);

        canvas = new StudioCanvas();
        root.addView(canvas,new LinearLayout.LayoutParams(-1,dp(300)));

        LinearLayout inspector = new LinearLayout(this);
        inspector.setOrientation(LinearLayout.VERTICAL);
        inspector.setPadding(dp(14),dp(10),dp(14),dp(10));
        inspector.setBackgroundColor(CARD);
        LinearLayout ih = new LinearLayout(this); ih.setGravity(Gravity.CENTER_VERTICAL);
        inspectorTitle = text("Aucun bloc sélectionné",15,true,TEXT); ih.addView(inspectorTitle,new LinearLayout.LayoutParams(0,-2,1));
        TextView hint = text("Touchez un bloc pour le régler",10,false,MUTED); ih.addView(hint); inspector.addView(ih);
        inspectorBody = text("Nom, type, sorties et actions du bloc apparaîtront ici.",11,false,MUTED); inspectorBody.setPadding(0,dp(5),0,dp(7)); inspector.addView(inspectorBody);
        inspectorActions = new LinearLayout(this); inspectorActions.setGravity(Gravity.CENTER_VERTICAL); inspector.addView(inspectorActions);
        root.addView(inspector);

        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.CENTER_VERTICAL); actions.setPadding(dp(10),dp(7),dp(10),dp(7)); actions.setBackgroundColor(CARD);
        Button undo=ghost("↶"); undo.setOnClickListener(v->canvas.undo()); actions.addView(undo,new LinearLayout.LayoutParams(dp(44),dp(44)));
        Button redo=ghost("↷"); redo.setOnClickListener(v->canvas.redo()); actions.addView(redo,new LinearLayout.LayoutParams(dp(44),dp(44)));
        Button test=ghost("▶ Tester"); test.setOnClickListener(v->{canvas.testFlow();saveServerWorkspace(true);}); actions.addView(test,new LinearLayout.LayoutParams(0,dp(44),1));
        environmentButton=ghost(environment); environmentButton.setOnClickListener(v->{environment="Brouillon".equals(environment)?"Test":"Test".equals(environment)?"Production":"Brouillon";refreshModeLabel();});actions.addView(environmentButton,new LinearLayout.LayoutParams(dp(100),dp(44)));
        Button publish=primary("Publier"); publish.setOnClickListener(v->publishServerWorkspace()); actions.addView(publish,new LinearLayout.LayoutParams(dp(96),dp(44)));
        root.addView(actions);

        HorizontalScrollView palette = new HorizontalScrollView(this); palette.setHorizontalScrollBarEnabled(false); palette.setBackgroundColor(BG);
        LinearLayout p = new LinearLayout(this); p.setPadding(dp(8),dp(8),dp(8),dp(12));
        addPalette(p,"Déclencheur",0); addPalette(p,"Condition",1); addPalette(p,"Action",2); addPalette(p,"Attente",3); addPalette(p,"Validation admin",4); addPalette(p,"Boucle",5); addPalette(p,"Sous-flux",6); addPalette(p,"Variable",7); addPalette(p,"Notification",2); addPalette(p,"QR Code",2); addPalette(p,"Réservation",2); addPalette(p,"Message",2); addPalette(p,"Points / Badge",2); addPalette(p,"Webhook/API",2);
        palette.addView(p); root.addView(palette);

        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        page.setBackgroundColor(BG);
        page.addView(root,new ScrollView.LayoutParams(-1,-2));
        setContentView(page);
        AppUiPrefs.applySafeArea(page);
    }



    private JSONObject workspaceDocument() throws Exception { return workspaceDocument(environment); }

    private JSONObject workspaceDocument(String documentEnvironment) throws Exception {
        if(canvas!=null) modeStates.put(mode,canvas.snapshot());
        JSONObject doc=new JSONObject();
        doc.put("title","Android Studio · espace complet");
        doc.put("description","Espace Android Studio · cible "+target+" · environnement "+documentEnvironment);
        JSONArray blocks=new JSONArray();
        blocks.put(new JSONObject().put("id","meta").put("kind","heading").put("text","UPSTUDIO2|"+mode+"|"+target+"|"+documentEnvironment+"|"+(registryVersion.isEmpty()?registryVersion():registryVersion)));
        int stateIndex=0;
        for(String m:STUDIO_MODES){
            Snapshot snap=modeStates.get(m);
            if(snap==null) continue;
            JSONObject state=new JSONObject(); JSONArray nn=new JSONArray(), ll=new JSONArray();
            for(Node n:snap.nodes){
                nn.put(new JSONObject().put("label",n.label).put("nodeKind",n.kind).put("x",n.x).put("y",n.y).put("type",n.type).put("config1",n.config1).put("config2",n.config2).put("config3",n.config3));
            }
            for(int[] l:snap.links) ll.put(new JSONArray().put(l[0]).put(l[1]));
            state.put("nodes",nn).put("links",ll);
            String encoded=Base64.encodeToString(state.toString().getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);
            int chunkSize=1450, total=Math.max(1,(encoded.length()+chunkSize-1)/chunkSize);
            for(int part=0;part<total;part++){
                if(blocks.length()>=500) throw new IllegalStateException("Le Studio contient trop de blocs pour une seule sauvegarde. Supprimez quelques blocs inutiles.");
                int from=part*chunkSize,to=Math.min(encoded.length(),from+chunkSize);
                String payload="UPSTUDIO2STATE|"+m+"|"+part+"|"+total+"|"+encoded.substring(from,to);
                blocks.put(new JSONObject().put("id","state-"+(stateIndex++)).put("kind","text").put("text",payload));
            }
        }
        doc.put("blocks",blocks); return doc;
    }

    private void loadServerWorkspace(){
        if(store==null||store.token().isEmpty()) return;
        io.execute(()->{try{
            JSONObject r=ApiClient.get("/api/studio",store.token()); JSONArray pages=r.optJSONArray("pages"); JSONObject page=null;
            if(pages!=null)for(int i=0;i<pages.length();i++){JSONObject x=pages.optJSONObject(i);if(x!=null&&WORKSPACE_SLUG.equals(x.optString("slug"))){page=x;break;}}
            if(page==null)return; serverPageId=page.optString("id",""); serverRevision=page.optInt("revision",1); String draft=page.optString("draft","");
            if(!draft.isEmpty()) runOnUiThread(()->restoreWorkspace(draft));
        }catch(Exception ignored){}});
    }

    private void restoreWorkspace(String raw){
        try{
            JSONObject doc=new JSONObject(raw); JSONArray blocks=doc.optJSONArray("blocks"); if(blocks==null||canvas==null)return;
            modeStates.clear();
            registryVersion="";
            int previousAutomationCount=0;
            ArrayList<Node> legacyNodes=new ArrayList<>(); ArrayList<int[]> legacyLinks=new ArrayList<>(); String legacyMode="Automatisations";
            Map<String,Map<Integer,String>> chunks=new LinkedHashMap<>(); Map<String,Integer> totals=new LinkedHashMap<>();
            for(int i=0;i<blocks.length();i++){
                JSONObject b=blocks.optJSONObject(i);if(b==null)continue; String text=b.optString("text","");
                if("meta".equals(b.optString("id"))&&text.startsWith("UPSTUDIO2|")){String[] a=text.split("\\|",-1);if(a.length>1)mode=a[1];if(a.length>2)target=a[2];if(a.length>3)environment=a[3];if(a.length>4)registryVersion=a[4];continue;}
                if("meta".equals(b.optString("id"))&&text.startsWith("UPSTUDIO|")){String[] a=text.split("\\|",-1);if(a.length>1){mode=a[1];legacyMode=a[1];}if(a.length>2)target=a[2];if(a.length>3)environment=a[3];continue;}
                if(text.startsWith("UPSTUDIO2STATE|")){
                    String[] a=text.split("\\|",5); if(a.length==5){String m=a[1];int part=Integer.parseInt(a[2]);int total=Integer.parseInt(a[3]);chunks.computeIfAbsent(m,k->new LinkedHashMap<>()).put(part,a[4]);totals.put(m,total);} continue;
                }
                if(!text.startsWith("{"))continue; JSONObject x=new JSONObject(text);String entity=x.optString("entity");
                if("node".equals(entity)){Node n=new Node(x.optString("label","Bloc"),x.optString("nodeKind","ACTION"),(float)x.optDouble("x",30),(float)x.optDouble("y",20),x.optInt("type",2));n.config1=x.optString("config1","");n.config2=x.optString("config2","");n.config3=x.optString("config3","");legacyNodes.add(n);} else if("link".equals(entity))legacyLinks.add(new int[]{x.optInt("from"),x.optInt("to")});
            }
            for(Map.Entry<String,Map<Integer,String>> entry:chunks.entrySet()){
                int total=totals.containsKey(entry.getKey())?totals.get(entry.getKey()):entry.getValue().size(); StringBuilder encoded=new StringBuilder(); boolean complete=true;
                for(int part=0;part<total;part++){String piece=entry.getValue().get(part);if(piece==null){complete=false;break;}encoded.append(piece);}
                if(!complete)continue;
                String decoded=new String(Base64.decode(encoded.toString(),Base64.DEFAULT),StandardCharsets.UTF_8); JSONObject state=new JSONObject(decoded);
                modeStates.put(entry.getKey(),snapshotFromJson(state));
            }
            if(modeStates.isEmpty()&&!legacyNodes.isEmpty()) modeStates.put(legacyMode,new Snapshot(legacyNodes,legacyLinks));
            Snapshot oldAutomation=modeStates.get("Automatisations"); previousAutomationCount=oldAutomation==null?0:oldAutomation.nodes.size();
            boolean needsRegistryMigration=!registryVersion().equals(registryVersion);
            if(needsRegistryMigration)ensureComprehensiveModeStates();
            if(needsRegistryMigration&&previousAutomationCount>0&&previousAutomationCount<=4&&"Automatisations".equals(mode)) mode="Site complet";
            Snapshot current=modeStates.get(mode); if(current!=null) canvas.restore(current); else {canvas.createDefaultForMode(mode);modeStates.put(mode,canvas.snapshot());}
            canvas.selected=-1; canvas.undo.clear(); canvas.redo.clear(); updateInspector(-1); refreshModeLabel(); canvas.ensureCanvasHeight();
            Toast.makeText(this,"Brouillon Studio chargé depuis le serveur",Toast.LENGTH_SHORT).show();
        }catch(Exception e){Toast.makeText(this,"Studio : brouillon illisible, espace local conservé",Toast.LENGTH_LONG).show();}
    }

    private Snapshot snapshotFromJson(JSONObject state) throws Exception {
        ArrayList<Node> nodes=new ArrayList<>(); ArrayList<int[]> links=new ArrayList<>(); JSONArray nn=state.optJSONArray("nodes"), ll=state.optJSONArray("links");
        if(nn!=null)for(int i=0;i<nn.length();i++){JSONObject x=nn.optJSONObject(i);if(x==null)continue;Node n=new Node(x.optString("label","Bloc"),x.optString("nodeKind","ACTION"),(float)x.optDouble("x",30),(float)x.optDouble("y",20),x.optInt("type",2));n.config1=x.optString("config1","");n.config2=x.optString("config2","");n.config3=x.optString("config3","");nodes.add(n);}
        if(ll!=null)for(int i=0;i<ll.length();i++){JSONArray x=ll.optJSONArray(i);if(x!=null&&x.length()>=2){int a=x.optInt(0,-1),b=x.optInt(1,-1);if(a>=0&&b>=0&&a<nodes.size()&&b<nodes.size())links.add(new int[]{a,b});}}
        return new Snapshot(nodes,links);
    }

    private void switchStudioMode(String next){
        if(canvas==null||next==null||next.equals(mode))return;
        modeStates.put(mode,canvas.snapshot()); mode=next; canvas.selected=-1; updateInspector(-1);
        canvas.undo.clear(); canvas.redo.clear(); Snapshot saved=modeStates.get(next); if(saved!=null)canvas.restore(saved);else{canvas.createDefaultForMode(next);modeStates.put(next,canvas.snapshot());}
        refreshModeLabel(); canvas.ensureCanvasHeight();
    }

    private void saveServerWorkspace(boolean testAfter){
        if(store==null||store.token().isEmpty()){Toast.makeText(this,"Connexion requise",Toast.LENGTH_LONG).show();return;}
        Toast.makeText(this,"Enregistrement du Studio…",Toast.LENGTH_SHORT).show();
        io.execute(()->{try{
            JSONObject doc=workspaceDocument();
            if(serverPageId.isEmpty()){
                JSONObject body=new JSONObject().put("action","create").put("slug",WORKSPACE_SLUG).put("document",doc);
                JSONObject r=ApiClient.post("/api/studio",store.token(),body);serverPageId=r.optString("id","");serverRevision=1;
            }else{
                JSONObject body=new JSONObject().put("action","save").put("id",serverPageId).put("revision",serverRevision).put("document",doc);
                ApiClient.post("/api/studio",store.token(),body);serverRevision++;
            }
            if(testAfter){JSONObject test=new JSONObject().put("action","test").put("id",serverPageId).put("revision",serverRevision);JSONObject tr=ApiClient.post("/api/studio",store.token(),test);JSONObject report=tr.optJSONObject("report");runOnUiThread(()->Toast.makeText(this,report==null?"Brouillon enregistré et testé":"Test serveur OK · "+(report.optJSONArray("warnings")==null?0:report.optJSONArray("warnings").length())+" avertissement(s)",Toast.LENGTH_LONG).show());}
            else runOnUiThread(()->Toast.makeText(this,"Brouillon Studio enregistré sur le serveur",Toast.LENGTH_LONG).show());
        }catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Studio : "+e.getMessage(),Toast.LENGTH_LONG).show());}});
    }

    private void publishServerWorkspace(){
        if(store==null||store.token().isEmpty()){Toast.makeText(this,"Connexion requise",Toast.LENGTH_LONG).show();return;}
        new AlertDialog.Builder(this)
                .setTitle("Publier le Studio")
                .setMessage("Cible : "+target+"\n\nLe brouillon actuel sera enregistré, testé puis publié. La version de production précédente restera disponible pour retour arrière.")
                .setNegativeButton("Annuler",null)
                .setPositiveButton("Publier",(d,w)->publishCurrentWorkspaceNow())
                .show();
    }

    private void publishCurrentWorkspaceNow(){
        Toast.makeText(this,"Publication du Studio…",Toast.LENGTH_LONG).show();
        io.execute(()->{try{
            JSONObject doc=workspaceDocument("Production");
            if(serverPageId.isEmpty()){
                JSONObject create=new JSONObject().put("action","create").put("slug",WORKSPACE_SLUG).put("document",doc);
                JSONObject created=ApiClient.post("/api/studio",store.token(),create);serverPageId=created.optString("id","");serverRevision=1;
            }else{
                JSONObject save=new JSONObject().put("action","save").put("id",serverPageId).put("revision",serverRevision).put("document",doc);
                ApiClient.post("/api/studio",store.token(),save);serverRevision++;
            }
            JSONObject test=new JSONObject().put("action","test").put("id",serverPageId).put("revision",serverRevision);
            JSONObject tested=ApiClient.post("/api/studio",store.token(),test);
            JSONObject report=tested.optJSONObject("report");
            if(report!=null&&report.optInt("blockingErrors",0)>0)throw new IllegalStateException("Le test Studio contient des erreurs bloquantes.");
            JSONObject publish=new JSONObject().put("action","publish").put("id",serverPageId).put("revision",serverRevision).put("confirmed",true);
            ApiClient.post("/api/studio",store.token(),publish);
            environment="Production";
            runOnUiThread(()->{refreshModeLabel();Toast.makeText(this,"Publié · cible "+target+" · site et application synchronisés selon la cible",Toast.LENGTH_LONG).show();});
        }catch(Exception e){runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Publication impossible").setMessage(e.getMessage()+"\n\nLe brouillon reste enregistré. Corrige le point indiqué puis relance Tester / Publier.").setPositiveButton("OK",null).show());}});
    }

    private void showVisualSiteEditor(String initialPath){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(10),dp(8),dp(10),dp(8)); box.setBackgroundColor(BG);
        LinearLayout toolbar=new LinearLayout(this); toolbar.setGravity(Gravity.CENTER_VERTICAL);
        EditText pathInput=new EditText(this); String start=(initialPath==null||initialPath.isEmpty()||initialPath.contains("["))?"/":initialPath; pathInput.setText(start); pathInput.setSingleLine(true); pathInput.setHint("/reservations"); pathInput.setTextColor(TEXT); pathInput.setHintTextColor(MUTED);
        toolbar.addView(pathInput,new LinearLayout.LayoutParams(0,dp(44),1));
        Button open=ghost("Ouvrir"); toolbar.addView(open,new LinearLayout.LayoutParams(dp(92),dp(44)));
        Button minus=ghost("−"); toolbar.addView(minus,new LinearLayout.LayoutParams(dp(48),dp(44)));
        TextView zoomLabel=text("100%",12,true,TEXT); zoomLabel.setGravity(Gravity.CENTER); toolbar.addView(zoomLabel,new LinearLayout.LayoutParams(dp(62),dp(44)));
        Button plus=ghost("+"); toolbar.addView(plus,new LinearLayout.LayoutParams(dp(48),dp(44)));
        Button modeBtn=primary("Sélection"); toolbar.addView(modeBtn,new LinearLayout.LayoutParams(dp(105),dp(44)));
        box.addView(toolbar);
        TextView help=text("Sélection : touchez un texte, un lien ou une image pour le modifier. Navigation : utilisez normalement la page. Les changements restent dans le brouillon Pages jusqu’à publication.",11,false,MUTED); help.setPadding(dp(4),dp(6),dp(4),dp(8)); box.addView(help);
        WebView web=new WebView(this); WebSettings settings=web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true); settings.setSupportZoom(true); settings.setBuiltInZoomControls(false); settings.setDisplayZoomControls(false); settings.setLoadWithOverviewMode(false); settings.setUseWideViewPort(true);
        CookieManager.getInstance().setAcceptCookie(true); if(store!=null&&!store.token().isEmpty())CookieManager.getInstance().setCookie(AppConfig.BASE_URL,store.token());
        final boolean[] selecting={true}; final int[] zoom={100};
        web.addJavascriptInterface(new SiteEditorBridge(),"StudioBridge");
        final String allowedHost=Uri.parse(AppConfig.BASE_URL).getHost();
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request){
                Uri uri=request.getUrl(); String host=uri==null?null:uri.getHost();
                if(host!=null&&host.equalsIgnoreCase(allowedHost))return false;
                Toast.makeText(StudioActivity.this,"Le Studio visuel reste limité au site Unité Paintball.",Toast.LENGTH_LONG).show();
                return true;
            }
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){view.setVisibility(android.view.View.VISIBLE);}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,android.webkit.WebResourceError error){if(request.isForMainFrame()){view.setVisibility(android.view.View.GONE);help.setText("Connexion momentanément indisponible. Appuie sur Ouvrir pour réessayer la page. Les modifications non confirmées ne sont pas publiées.");}}
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,android.webkit.WebResourceResponse response){if(request.isForMainFrame()&&response.getStatusCode()>=500){view.setVisibility(android.view.View.GONE);help.setText("Site momentanément indisponible. Appuie sur Ouvrir pour réessayer.");}}
            @Override public void onPageFinished(WebView view,String url){ super.onPageFinished(view,url); applySiteZoom(view,zoom[0]); injectSiteSelector(view,selecting[0]); }
        });
        box.addView(web,new LinearLayout.LayoutParams(-1,dp(500)));
        Runnable navigate=()->{String path=pathInput.getText().toString().trim();if(path.isEmpty())path="/";if(!path.startsWith("/"))path="/"+path;if(path.startsWith("//")||path.startsWith("/api")||path.startsWith("/_next")||path.startsWith("/studio-p")||path.startsWith("/admin/editeur")){Toast.makeText(this,"Cette route est protégée dans le Studio visuel.",Toast.LENGTH_LONG).show();return;}web.loadUrl(AppConfig.BASE_URL+path);};
        open.setOnClickListener(v->navigate.run());
        minus.setOnClickListener(v->{zoom[0]=Math.max(40,zoom[0]-10);applySiteZoom(web,zoom[0]);zoomLabel.setText(zoom[0]+"%");});
        plus.setOnClickListener(v->{zoom[0]=Math.min(160,zoom[0]+10);applySiteZoom(web,zoom[0]);zoomLabel.setText(zoom[0]+"%");});
        modeBtn.setOnClickListener(v->{selecting[0]=!selecting[0];modeBtn.setText(selecting[0]?"Sélection":"Navigation");web.evaluateJavascript("window.__upStudioSelect="+(selecting[0]?"true":"false"),null);});
        navigate.run();
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Site complet · éditeur visuel").setView(box).setNegativeButton("Fermer",null).create();
        dialog.setOnDismissListener(d->web.destroy()); dialog.show();
    }

    private void applySiteZoom(WebView web,int percent){
        int safe=Math.max(40,Math.min(160,percent));
        web.setInitialScale(safe);
        web.evaluateJavascript("document.documentElement.style.zoom='"+safe+"%'",null);
    }

    private void injectSiteSelector(WebView web,boolean selecting){
        String script="(function(){window.__upStudioSelect="+(selecting?"true":"false")+";if(window.__upStudioInstalled)return;window.__upStudioInstalled=true;function path(el){var parts=[];while(el&&el!==document.body&&parts.length<12){var tag=el.tagName.toLowerCase(),p=el.parentElement;if(!p)break;var same=[].filter.call(p.children,function(c){return c.tagName===el.tagName});var idx=same.indexOf(el)+1;parts.unshift(tag+':nth-of-type('+Math.max(1,idx)+')');el=p;}return 'body > '+parts.join(' > ');}document.addEventListener('click',function(e){if(!window.__upStudioSelect)return;var el=e.target;if(!el||!el.closest)return;var img=el.closest('img'),a=el.closest('a');if(img)el=img;else if(a)el=a;var tag=(el.tagName||'').toLowerCase();if(['html','body','head','script','style','iframe'].indexOf(tag)>=0)return;e.preventDefault();e.stopPropagation();e.stopImmediatePropagation();if(window.__upStudioLast)window.__upStudioLast.style.outline='';window.__upStudioLast=el;el.style.outline='3px solid #20e5f3';el.style.outlineOffset='2px';StudioBridge.pick(location.pathname,path(el),tag,(el.textContent||'').trim(),a?a.getAttribute('href')||'':'',img?img.getAttribute('src')||'':'',img?img.getAttribute('alt')||'':'');},true);})();";
        web.evaluateJavascript(script,null);
    }

    private class SiteEditorBridge {
        @JavascriptInterface public void pick(String path,String selector,String tag,String text,String href,String src,String alt){ runOnUiThread(()->showVisualElementEditor(path,selector,tag,text,href,src,alt)); }
    }

    private void showVisualElementEditor(String path,String selector,String tag,String textValue,String hrefValue,String srcValue,String altValue){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18),dp(8),dp(18),0);
        TextView info=text(path+"\n"+selector,10,false,MUTED); info.setPadding(0,0,0,dp(8)); box.addView(info);
        EditText first=new EditText(this),second=null; first.setTextColor(TEXT); first.setHintTextColor(MUTED); first.setSingleLine(false);
        if("img".equals(tag)){first.setHint("URL de l’image");first.setText(srcValue);second=new EditText(this);second.setHint("Texte alternatif");second.setText(altValue);}
        else if("a".equals(tag)){first.setHint("Texte du lien");first.setText(textValue);second=new EditText(this);second.setHint("Destination /... ou https://...");second.setText(hrefValue);}
        else {first.setHint("Texte affiché");first.setText(textValue);}
        box.addView(first); if(second!=null){second.setTextColor(TEXT);second.setHintTextColor(MUTED);box.addView(second);}
        EditText finalSecond=second;
        new AlertDialog.Builder(this).setTitle("Modifier l’élément du site").setView(box).setNegativeButton("Annuler",null).setPositiveButton("Ajouter au brouillon",(d,w)->{
            try{
                JSONObject payload=new JSONObject();
                if("img".equals(tag))payload.put("kind","image").put("src",first.getText().toString().trim()).put("alt",finalSecond==null?"":finalSecond.getText().toString().trim());
                else if("a".equals(tag))payload.put("kind","link").put("text",first.getText().toString()).put("href",finalSecond==null?"":finalSecond.getText().toString().trim());
                else payload.put("kind","text").put("text",first.getText().toString());
                upsertPageVisualOverride(path,selector,tag,payload.toString());
            }catch(Exception e){Toast.makeText(this,"Modification impossible : "+e.getMessage(),Toast.LENGTH_LONG).show();}
        }).show();
    }

    private void upsertPageVisualOverride(String path,String selector,String tag,String payload){
        if(path==null||!path.startsWith("/")||path.startsWith("//")||path.startsWith("/api")||path.startsWith("/_next")||path.startsWith("/studio-p")||path.startsWith("/admin/editeur")||selector==null||selector.trim().isEmpty()){Toast.makeText(this,"Page ou élément invalide.",Toast.LENGTH_LONG).show();return;}
        if(canvas!=null)modeStates.put(mode,canvas.snapshot());
        Snapshot pages=modeStates.get("Pages");
        if(pages==null)pages=new Snapshot(new ArrayList<>(),new ArrayList<>());
        Node found=null;
        for(Node n:pages.nodes)if(path.equals(n.config1)&&selector.equals(n.config2)){found=n;break;}
        String label=("img".equals(tag)?"Image":"a".equals(tag)?"Lien":"Texte")+" · "+path;
        if(found==null){found=new Node(label,"CONTENU",24+(pages.nodes.size()%3)*196,18+(pages.nodes.size()/3)*104,2);pages.nodes.add(found);}
        found.label=label;found.kind="CONTENU";found.config1=path;found.config2=selector;found.config3=payload;modeStates.put("Pages",pages);
        if("Pages".equals(mode)&&canvas!=null){canvas.restore(pages);canvas.selected=pages.nodes.indexOf(found);updateInspector(canvas.selected);}
        Toast.makeText(this,"Modification ajoutée à Pages · Enregistrer, Tester puis Publier pour l’appliquer.",Toast.LENGTH_LONG).show();
    }

    private boolean isSiteRouteNode(Node n){ return n!=null && SITE_ROUTE_MARKER.equals(n.config2); }

    private String siteRouteLabel(String path){
        if("/".equals(path)) return "Accueil";
        String[] parts=path.split("/"); String last=parts.length==0?"Page":parts[parts.length-1];
        if(last==null||last.isEmpty()) last="Page"; last=last.replace("[","").replace("]","").replace("-"," ");
        StringBuilder out=new StringBuilder(); boolean upper=true; for(char c:last.toCharArray()){if(upper&&Character.isLetter(c)){out.append(Character.toUpperCase(c));upper=false;}else{out.append(c);upper=c==' ';}} return out.toString();
    }

    private JSONObject studioRegistry(){
        if(registryCache!=null)return registryCache;
        try(InputStream in=getAssets().open("studio-registry.json"); ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int read;while((read=in.read(buffer))!=-1)out.write(buffer,0,read);
            registryCache=new JSONObject(out.toString("UTF-8"));
        }catch(Exception e){registryCache=new JSONObject();}
        return registryCache;
    }

    private Snapshot registrySnapshot(String m){
        try{
            JSONObject states=studioRegistry().optJSONObject("states");
            JSONObject state=states==null?null:states.optJSONObject(m);
            if(state!=null)return snapshotFromJson(state);
        }catch(Exception ignored){}
        return new Snapshot(new ArrayList<>(),new ArrayList<>());
    }

    private int registryCount(String key){
        JSONObject counts=studioRegistry().optJSONObject("counts");
        return counts==null?0:counts.optInt(key,0);
    }

    private String registryVersion(){
        return studioRegistry().optString("version","2.2.24");
    }

    private Snapshot buildCompleteSiteMapSnapshot(){ return registrySnapshot("Pages"); }

    private Snapshot defaultSnapshotForMode(String m){
        Snapshot fromRegistry=registrySnapshot(m);
        if(fromRegistry!=null&&!fromRegistry.nodes.isEmpty())return fromRegistry;
        ArrayList<Node> nodes=new ArrayList<>(); ArrayList<int[]> links=new ArrayList<>();
        if("Automatisations".equals(m)){
            nodes.add(new Node("Nouvelle réservation","DÉCLENCHEUR",24,18,0));
            nodes.add(new Node("Places disponibles ?","CONDITION",220,18,1));
            nodes.add(new Node("Calculer tarif","ACTION",220,118,2));
            nodes.add(new Node("Créer QR Code","ACTION",24,118,2));
            links.add(new int[]{0,1});links.add(new int[]{1,2});links.add(new int[]{2,3});
        }else{
            nodes.add(new Node(m,"ÉDITEUR",24,18,2));
            nodes.add(new Node("Ajouter un bloc","CONTENU",220,18,2));
            links.add(new int[]{0,1});
        }
        return new Snapshot(nodes,links);
    }

    private String nodeKey(Node n){
        String label=n==null||n.label==null?"":n.label.trim().toLowerCase(java.util.Locale.FRANCE);
        String c1=n==null||n.config1==null?"":n.config1.trim();
        String marker=n==null||n.config2==null?"":n.config2;
        if(!SITE_ROUTE_MARKER.equals(marker)&&!SITE_MODULE_MARKER.equals(marker)&&!API_ACTION_MARKER.equals(marker))marker="";
        return marker+"|"+label+"|"+c1;
    }
    private String nodeLabelKey(Node n){return n==null||n.label==null?"":n.label.trim().toLowerCase(java.util.Locale.FRANCE);}

    private void ensureComprehensiveModeStates(){
        for(String m:STUDIO_MODES){
            Snapshot defaults=defaultSnapshotForMode(m);
            Snapshot current=modeStates.get(m);
            if(current==null){modeStates.put(m,defaults);continue;}
            java.util.HashMap<String,Integer> exact=new java.util.HashMap<>();
            java.util.HashMap<String,Integer> labels=new java.util.HashMap<>();
            for(int i=0;i<current.nodes.size();i++){Node n=current.nodes.get(i);exact.put(nodeKey(n),i);labels.put(nodeLabelKey(n),i);}
            java.util.HashMap<Integer,Integer> map=new java.util.HashMap<>();
            for(int i=0;i<defaults.nodes.size();i++){
                Node seed=defaults.nodes.get(i);Integer existing=exact.get(nodeKey(seed));if(existing==null)existing=labels.get(nodeLabelKey(seed));
                if(existing==null){Node c=new Node(seed.label,seed.kind,seed.x,seed.y,seed.type);c.config1=seed.config1;c.config2=seed.config2;c.config3=seed.config3;current.nodes.add(c);existing=current.nodes.size()-1;exact.put(nodeKey(c),existing);labels.put(nodeLabelKey(c),existing);}
                map.put(i,existing);
            }
            java.util.HashSet<String> linkKeys=new java.util.HashSet<>();for(int[] l:current.links)linkKeys.add(l[0]+":"+l[1]);
            for(int[] l:defaults.links){Integer a=map.get(l[0]),b=map.get(l[1]);if(a==null||b==null)continue;String key=a+":"+b;if(linkKeys.add(key))current.links.add(new int[]{a,b});}
            modeStates.put(m,current);
        }
        registryVersion=registryVersion();
    }

    private void ensureDefaultModeStates(){ ensureComprehensiveModeStates(); }

    private void ensureCompleteSiteMapState(){ ensureComprehensiveModeStates(); }

    private boolean isSiteModuleNode(Node n){ return n!=null && SITE_MODULE_MARKER.equals(n.config2); }
    private boolean isOpenableStudioNode(Node n){ return n!=null&&(isSiteRouteNode(n)||isSiteModuleNode(n))&&n.config1!=null&&!n.config1.startsWith("/admin/editeur"); }

    private void updateInspector(int index){
        if(inspectorTitle==null||inspectorBody==null||inspectorActions==null) return;
        inspectorActions.removeAllViews();
        if(canvas==null || index<0 || index>=canvas.nodes.size()){
            inspectorTitle.setText("Aucun bloc sélectionné");
            inspectorBody.setText("Touchez un bloc du canvas pour modifier son nom, le dupliquer, le supprimer ou gérer ses sorties.");
            return;
        }
        Node n=canvas.nodes.get(index);
        inspectorTitle.setText(n.label);
        String outputs=n.type==1?"Sorties : OUI / NON":"Sortie principale →";
        String details=n.summary();
        inspectorBody.setText(n.kind+" · "+outputs+" · Cible : "+target+" · "+environment+(details.isEmpty()?"":"\n"+details));
        if(isOpenableStudioNode(n)){ Button openPage=primary("🌐 Ouvrir"); openPage.setOnClickListener(v->showVisualSiteEditor(n.config1)); inspectorActions.addView(openPage,new LinearLayout.LayoutParams(0,dp(40),1)); }
        Button rename=ghost("✎ Nom"); rename.setOnClickListener(v->renameNode(index)); inspectorActions.addView(rename,new LinearLayout.LayoutParams(0,dp(40),1));
        Button config=ghost("⚙ Régler"); config.setOnClickListener(v->configureNode(index)); inspectorActions.addView(config,new LinearLayout.LayoutParams(0,dp(40),1));
        Button duplicate=ghost("⧉ Dupliquer"); duplicate.setOnClickListener(v->canvas.duplicateNode(index)); inspectorActions.addView(duplicate,new LinearLayout.LayoutParams(0,dp(40),1));
        Button link=ghost("＋ Sortie"); link.setOnClickListener(v->canvas.addOutputNode(index)); inspectorActions.addView(link,new LinearLayout.LayoutParams(0,dp(40),1));
        Button del=ghost("Supprimer"); del.setTextColor(ORANGE); del.setOnClickListener(v->canvas.deleteNode(index)); inspectorActions.addView(del,new LinearLayout.LayoutParams(0,dp(40),1));
    }

    private void renameNode(int index){
        if(canvas==null||index<0||index>=canvas.nodes.size()) return;
        Node n=canvas.nodes.get(index);
        EditText input=new EditText(this); input.setText(n.label); input.setSelectAllOnFocus(true); input.setTextColor(TEXT); input.setHintTextColor(MUTED); input.setPadding(dp(14),dp(12),dp(14),dp(12));
        new AlertDialog.Builder(this).setTitle("Nom du bloc").setView(input).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",(d,w)->{
            String value=input.getText().toString().trim(); if(value.isEmpty()) return; canvas.push(); n.label=value; canvas.invalidate(); updateInspector(index);
        }).show();
    }


    private void configureNode(int index){
        if(canvas==null||index<0||index>=canvas.nodes.size()) return;
        Node n=canvas.nodes.get(index);
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18),dp(8),dp(18),0);
        TextView help=text(configHelp(n),11,false,MUTED); help.setPadding(0,0,0,dp(8)); box.addView(help);
        EditText a=configField(configLabel1(n),n.config1); box.addView(a);
        EditText b=configField(configLabel2(n),n.config2); box.addView(b);
        EditText c=configField(configLabel3(n),n.config3); box.addView(c);
        new AlertDialog.Builder(this).setTitle("Réglages · "+n.label).setView(box).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",(d,w)->{
            canvas.push(); n.config1=a.getText().toString().trim(); n.config2=b.getText().toString().trim(); n.config3=c.getText().toString().trim(); canvas.invalidate(); updateInspector(index);
        }).show();
    }

    private EditText configField(String hint,String value){
        EditText e=new EditText(this); e.setHint(hint); e.setText(value==null?"":value); e.setTextColor(TEXT); e.setHintTextColor(MUTED); e.setSingleLine(false); e.setPadding(dp(12),dp(10),dp(12),dp(10)); return e;
    }
    private String configHelp(Node n){
        if("Menus".equals(mode)) return "Menu partagé : destination (ex. /reservations), description et catégorie. Après publication, la cible Site/Application/Les deux décide où le changement est appliqué.";
        if("Site complet".equals(mode)) return "Module du site complet : la première valeur pointe vers sa page. Utilisez le bouton Ouvrir pour afficher et modifier la page quand elle est éditable.";
        if("Gamification".equals(mode)) return "Règles de progression partagées : XP, Points Unité, rangs, missions, badges, roulette, coffres et récompenses. Les valeurs publiées sont appliquées côté serveur.";
        if("Pages".equals(mode)) return "Modification visuelle du site : page publique, sélecteur de l’élément et JSON sécurisé (texte, lien ou image). Utilisez de préférence le bouton Site complet pour créer ces réglages automatiquement.";
        if(n.type==1) return "Condition : variable + opérateur + valeur. Ex. reservation.nombre_joueurs | supérieur à | 10";
        String l=n.label.toLowerCase();
        if(l.contains("attente")) return "Temporisation : durée, unité ou date cible. Ex. 72 | heures | avant reservation.date";
        if(l.contains("notification")) return "Notification : canal, destinataire et contenu. Les variables {{...}} sont autorisées.";
        if(l.contains("message")) return "Message : conversation/cible, texte et éventuellement modèle.";
        if(l.contains("qr")) return "QR Code : type d'objet, identifiant source et action au scan.";
        if(l.contains("points")||l.contains("badge")) return "Récompense : nombre de points ou badge, destinataire et motif.";
        if(l.contains("webhook")||l.contains("api")) return "API/Webhook : URL HTTPS, méthode et corps JSON.";
        if(l.contains("variable")) return "Variable : nom, valeur ou expression et portée.";
        if(l.contains("validation")) return "Validation admin : rôle autorisé, délai et comportement si refus.";
        if(l.contains("boucle")) return "Boucle : collection, alias de l'élément et limite de sécurité.";
        if(l.contains("sous-flux")) return "Sous-flux : nom du workflow réutilisable et paramètres transmis.";
        if(l.contains("déclencheur")||n.type==0) return "Déclencheur : événement, filtre facultatif et source.";
        return "Action : renseignez la cible, l'opération et les paramètres nécessaires.";
    }
    private String configLabel1(Node n){
        if("Menus".equals(mode)) return "Destination : /reservations, /qr, /galerie…";
        if("Pages".equals(mode)) return "Page publique : /, /reservations…";
        if(n.type==1) return "Variable (ex. joueur.age)";
        String l=n.label.toLowerCase();
        if(l.contains("attente")) return "Durée / date";
        if(l.contains("notification")) return "Canal : push, email, SMS...";
        if(l.contains("message")) return "Destinataire / groupe";
        if(l.contains("qr")) return "Type : joueur, réservation, partie...";
        if(l.contains("points")||l.contains("badge")) return "Points ou badge";
        if(l.contains("webhook")||l.contains("api")) return "URL HTTPS";
        if(l.contains("variable")) return "Nom de variable";
        if(l.contains("validation")) return "Rôle validateur";
        if(l.contains("boucle")) return "Collection (ex. reservation.joueurs)";
        if(l.contains("sous-flux")) return "Nom du sous-flux";
        if(l.contains("déclencheur")||n.type==0) return "Événement (ex. reservation.created)";
        return "Cible / ressource";
    }
    private String configLabel2(Node n){
        if("Menus".equals(mode)) return "Description affichée dans le menu";
        if("Pages".equals(mode)) return "Sélecteur CSS de l’élément";
        if(n.type==1) return "Opérateur : =, ≠, >, <, contient, vide...";
        String l=n.label.toLowerCase();
        if(l.contains("attente")) return "Unité / référence temporelle";
        if(l.contains("notification")) return "Destinataire (ex. joueur, admin)";
        if(l.contains("message")) return "Contenu du message";
        if(l.contains("qr")) return "Identifiant source / variable";
        if(l.contains("points")||l.contains("badge")) return "Destinataire";
        if(l.contains("webhook")||l.contains("api")) return "Méthode : GET, POST, PATCH...";
        if(l.contains("variable")) return "Valeur / expression";
        if(l.contains("validation")) return "Délai maximum";
        if(l.contains("boucle")) return "Alias élément (ex. joueur)";
        if(l.contains("sous-flux")) return "Paramètres transmis";
        if(l.contains("déclencheur")||n.type==0) return "Filtre facultatif";
        return "Action / opération";
    }
    private String configLabel3(Node n){
        if("Menus".equals(mode)) return "Catégorie (ex. Réserver & venir)";
        if("Pages".equals(mode)) return "JSON : {\"kind\":\"text\",\"text\":\"...\"}";
        if(n.type==1) return "Valeur comparée";
        String l=n.label.toLowerCase();
        if(l.contains("attente")) return "Action si délai dépassé";
        if(l.contains("notification")) return "Texte : Bonjour {{joueur.prenom}}...";
        if(l.contains("message")) return "Modèle / options";
        if(l.contains("qr")) return "Action au scan";
        if(l.contains("points")||l.contains("badge")) return "Motif";
        if(l.contains("webhook")||l.contains("api")) return "Corps JSON / paramètres";
        if(l.contains("variable")) return "Portée : workflow, joueur, globale";
        if(l.contains("validation")) return "Si refus / expiration";
        if(l.contains("boucle")) return "Limite max";
        if(l.contains("sous-flux")) return "Résultat attendu";
        if(l.contains("déclencheur")||n.type==0) return "Source / portée";
        return "Paramètres / valeur";
    }

    private void refreshModeLabel(){
        String extra="";
        if("Site complet".equals(mode))extra=" · "+registryCount("modules")+" modules";
        else if("Pages".equals(mode))extra=" · "+registryCount("pages")+" pages";
        else if("API / Actions".equals(mode))extra=" · "+registryCount("apiActions")+" actions";
        if(modeLabel!=null) modeLabel.setText(mode+extra+" · "+target+" · "+environment);
        if(targetButton!=null) targetButton.setText("Cible : "+target);
        if(environmentButton!=null) environmentButton.setText(environment);
    }

    private void addPalette(LinearLayout row,String label,int type){ Button b=chip("＋ "+label); b.setOnClickListener(v->{if("Action".equals(label))showActionCatalog();else canvas.addNode(label,type);}); row.addView(b); }

    private void showActionCatalog(){
        if(canvas==null)return;
        final String[] actions={"Action personnalisée","Vérifier disponibilité","Calculer tarif","Créer réservation","Créer QR Code","Check-in joueur","Envoyer notification","Envoyer message","Ajouter points / badge","Inscrire participation","Valider adhésion","Mettre en liste d’attente","Mettre à jour stock","Créer incident","Webhook / API"};
        new AlertDialog.Builder(this).setTitle("Bibliothèque d’actions existantes").setItems(actions,(d,which)->canvas.addActionTemplate(actions[which])).setNegativeButton("Annuler",null).show();
    }

    private class StudioCanvas extends View {
        final Paint grid=new Paint(1), line=new Paint(1), title=new Paint(1), sub=new Paint(1), border=new Paint(1), fill=new Paint(1);
        final List<Node> nodes=new ArrayList<>(); final List<int[]> links=new ArrayList<>();
        int selected=-1; float dx,dy; boolean testing=false;
        final List<Snapshot> undo=new ArrayList<>(), redo=new ArrayList<>();

        StudioCanvas(){ super(StudioActivity.this); setBackgroundColor(BG);
            grid.setColor(Color.argb(20,255,255,255)); grid.setStrokeWidth(dpF(1));
            line.setColor(Color.argb(150,157,255,53)); line.setStrokeWidth(dpF(2));
            border.setStyle(Paint.Style.STROKE); border.setStrokeWidth(dpF(1.4f));
            title.setTextSize(dpF(14)); title.setTypeface(Typeface.DEFAULT_BOLD); title.setColor(TEXT);
            sub.setTextSize(dpF(10)); sub.setColor(MUTED);
            seed();
        }
        void seed(){ nodes.clear();links.clear();Snapshot snap=defaultSnapshotForMode(mode);for(Node n:snap.nodes){Node c=new Node(n.label,n.kind,n.x,n.y,n.type);c.config1=n.config1;c.config2=n.config2;c.config3=n.config3;nodes.add(c);}for(int[] l:snap.links)links.add(new int[]{l[0],l[1]});invalidate();ensureCanvasHeight(); }
        void createDefaultForMode(String m){ selected=-1; updateInspector(-1);Snapshot snap=defaultSnapshotForMode(m);restore(snap);undo.clear();redo.clear(); }
        void addNode(String label,int type){ push(); float x=24+(nodes.size()%2)*196; float y=18+(nodes.size()/2)*104; nodes.add(new Node(label,typeName(type),x,y,type)); if(nodes.size()>1)links.add(new int[]{nodes.size()-2,nodes.size()-1});selected=nodes.size()-1;invalidate();ensureCanvasHeight();updateInspector(selected); }
        void addActionTemplate(String label){
            addNode(label,2); if(selected<0||selected>=nodes.size())return; Node n=nodes.get(selected);
            if("Vérifier disponibilité".equals(label)){n.config1="réservation";n.config2="vérifier disponibilité";n.config3="date · heure · joueurs";}
            else if("Calculer tarif".equals(label)){n.config1="réservation";n.config2="calculer tarif";n.config3="formule · joueurs · durée";}
            else if("Créer réservation".equals(label)){n.config1="réservation";n.config2="créer";n.config3="joueur · créneau · formule";}
            else if("Créer QR Code".equals(label)){n.config1="réservation";n.config2="reservation.id";n.config3="check-in";}
            else if("Check-in joueur".equals(label)){n.config1="joueur / réservation";n.config2="enregistrer arrivée";n.config3="QR ou identifiant";}
            else if("Envoyer notification".equals(label)){n.config1="push";n.config2="joueur";n.config3="Message de notification";}
            else if("Envoyer message".equals(label)){n.config1="joueur / groupe";n.config2="Message";n.config3="conversation";}
            else if("Ajouter points / badge".equals(label)){n.config1="points / badge";n.config2="joueur";n.config3="motif";}
            else if("Inscrire participation".equals(label)){n.config1="partie / événement";n.config2="ajouter participation";n.config3="joueur";}
            else if("Valider adhésion".equals(label)){n.config1="adhésion";n.config2="valider";n.config3="membre";}
            else if("Mettre en liste d’attente".equals(label)){n.config1="réservation";n.config2="liste d’attente";n.config3="joueur";}
            else if("Mettre à jour stock".equals(label)){n.config1="stock / matériel";n.config2="mettre à jour";n.config3="quantité";}
            else if("Créer incident".equals(label)){n.config1="incident";n.config2="créer";n.config3="joueur / partie";}
            else if("Webhook / API".equals(label)){n.config1="https://";n.config2="POST";n.config3="{}";}
            invalidate();updateInspector(selected);
        }
        void duplicateNode(int index){ if(index<0||index>=nodes.size())return; push(); Node src=nodes.get(index); Node copy=new Node(src.label+" (copie)",src.kind,src.x+24,src.y+24,src.type); copy.config1=src.config1; copy.config2=src.config2; copy.config3=src.config3; nodes.add(copy); selected=nodes.size()-1; invalidate(); ensureCanvasHeight(); updateInspector(selected); }
        void deleteNode(int index){ if(index<0||index>=nodes.size())return; push(); nodes.remove(index); for(int i=links.size()-1;i>=0;i--){int[] l=links.get(i); if(l[0]==index||l[1]==index)links.remove(i); else {if(l[0]>index)l[0]--;if(l[1]>index)l[1]--;}} selected=-1; invalidate(); ensureCanvasHeight(); updateInspector(-1); }
        void addOutputNode(int index){ if(index<0||index>=nodes.size())return; push(); Node src=nodes.get(index); String label=src.type==1?"Branche condition":"Action suivante"; int t=src.type==1?2:2; Node next=new Node(label,typeName(t),src.x+190,src.y+105,t); nodes.add(next); links.add(new int[]{index,nodes.size()-1}); selected=nodes.size()-1; invalidate(); ensureCanvasHeight(); updateInspector(selected); }
        String typeName(int t){String[] x={"DÉCLENCHEUR","CONDITION","ACTION","ATTENTE","VALIDATION","BOUCLE","SOUS-FLUX","VARIABLE"};return x[Math.max(0,Math.min(x.length-1,t))];}
        @Override protected void onDraw(Canvas c){super.onDraw(c);drawGrid(c);for(int[] l:links)drawLink(c,l);for(int i=0;i<nodes.size();i++)drawNode(c,nodes.get(i),i);}
        void drawGrid(Canvas c){int s=dp(24);for(int x=0;x<getWidth();x+=s)c.drawLine(x,0,x,getHeight(),grid);for(int y=0;y<getHeight();y+=s)c.drawLine(0,y,getWidth(),y,grid);}
        void drawLink(Canvas c,int[] l){if(l[0]>=nodes.size()||l[1]>=nodes.size())return;Node a=nodes.get(l[0]),b=nodes.get(l[1]);line.setColor(testing?ORANGE:Color.argb(150,157,255,53));float x1=dpF(a.x+150),y1=dpF(a.y+38),x2=dpF(b.x),y2=dpF(b.y+38);c.drawLine(x1,y1,x2,y2,line);c.drawCircle(x1,y1,dpF(4),line);c.drawCircle(x2,y2,dpF(4),line);}
        void drawNode(Canvas c,Node n,int index){float l=dpF(n.x),t=dpF(n.y),r=l+dpF(150),b=t+dpF(78);RectF rr=new RectF(l,t,r,b);fill.setStyle(Paint.Style.FILL);fill.setColor(CARD);c.drawRoundRect(rr,dpF(14),dpF(14),fill);border.setColor(index==selected?LIME:Color.argb(55,255,255,255));c.drawRoundRect(rr,dpF(14),dpF(14),border);Paint badge=new Paint(1);badge.setColor(n.type==1?ORANGE:LIME);c.drawCircle(l+dpF(17),t+dpF(17),dpF(5),badge);c.drawText(n.kind,l+dpF(28),t+dpF(21),sub);c.drawText(trim(n.label,20),l+dpF(12),t+dpF(49),title);String footer=n.type==1?"OUI  /  NON":"Sortie →"; if(!n.config1.isEmpty()) footer=trim(n.config1,18); c.drawText(footer,l+dpF(12),t+dpF(68),sub);}
        String trim(String s,int n){return s.length()>n?s.substring(0,n-1)+"…":s;}
        @Override public boolean onTouchEvent(MotionEvent e){float x=e.getX()/getResources().getDisplayMetrics().density,y=e.getY()/getResources().getDisplayMetrics().density;if(e.getAction()==MotionEvent.ACTION_DOWN){int hit=find(x,y);selected=hit;updateInspector(selected);if(selected>=0){getParent().requestDisallowInterceptTouchEvent(true);push();Node n=nodes.get(selected);dx=x-n.x;dy=y-n.y;invalidate();return true;}invalidate();return true;}else if(e.getAction()==MotionEvent.ACTION_MOVE&&selected>=0){Node n=nodes.get(selected);n.x=Math.max(0,x-dx);n.y=Math.max(0,y-dy);invalidate();ensureCanvasHeight();return true;}else if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){getParent().requestDisallowInterceptTouchEvent(false);invalidate();ensureCanvasHeight();return true;}return true;}
        int find(float x,float y){for(int i=nodes.size()-1;i>=0;i--){Node n=nodes.get(i);if(x>=n.x&&x<=n.x+150&&y>=n.y&&y<=n.y+78)return i;}return -1;}
        void testFlow(){testing=true;invalidate();postDelayed(()->{testing=false;invalidate();},1800);}
        void push(){undo.add(snapshot());if(undo.size()>30)undo.remove(0);redo.clear();}
        Snapshot snapshot(){List<Node> nn=new ArrayList<>();for(Node n:nodes){Node c=new Node(n.label,n.kind,n.x,n.y,n.type);c.config1=n.config1;c.config2=n.config2;c.config3=n.config3;nn.add(c);}List<int[]> ll=new ArrayList<>();for(int[] x:links)ll.add(new int[]{x[0],x[1]});return new Snapshot(nn,ll);}
        void restore(Snapshot s){nodes.clear();for(Node n:s.nodes){Node c=new Node(n.label,n.kind,n.x,n.y,n.type);c.config1=n.config1;c.config2=n.config2;c.config3=n.config3;nodes.add(c);}links.clear();for(int[] x:s.links)links.add(new int[]{x[0],x[1]});invalidate();ensureCanvasHeight();}
        void ensureCanvasHeight(){float max=0;for(Node n:nodes)max=Math.max(max,n.y+100);int wanted=dp(Math.max(260,max));android.view.ViewGroup.LayoutParams lp=getLayoutParams();if(lp!=null&&lp.height!=wanted){lp.height=wanted;setLayoutParams(lp);}requestLayout();}

        void undo(){if(undo.isEmpty())return;redo.add(snapshot());restore(undo.remove(undo.size()-1));}
        void redo(){if(redo.isEmpty())return;undo.add(snapshot());restore(redo.remove(redo.size()-1));}
    }
    private static class Node {String label,kind;float x,y;int type;String config1="",config2="",config3="";Node(String l,String k,float x,float y,int t){label=l;kind=k;this.x=x;this.y=y;type=t;}String summary(){StringBuilder s=new StringBuilder();if(config1!=null&&!config1.isEmpty())s.append(config1);if(config2!=null&&!config2.isEmpty()){if(s.length()>0)s.append(" · ");s.append(config2);}if(config3!=null&&!config3.isEmpty()){if(s.length()>0)s.append(" · ");s.append(config3);}return s.toString();}}
    private static class Snapshot {List<Node> nodes;List<int[]> links;Snapshot(List<Node>n,List<int[]>l){nodes=n;links=l;}}

    private TextView text(String s,int sp,boolean bold,int color){TextView v=new TextView(this);v.setText(AppUiPrefs.t(this,s));v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private Button chip(String s){Button b=new Button(this);b.setText(AppUiPrefs.t(this,s));b.setAllCaps(false);b.setTextColor(TEXT);b.setTextSize(12);b.setBackgroundColor(CARD);b.setPadding(dp(12),0,dp(12),0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(42));p.setMargins(dp(4),0,dp(4),0);b.setLayoutParams(p);return b;}
    private Button ghost(String s){Button b=chip(s);b.setTextColor(LIME);return b;}
    private Button primary(String s){Button b=new Button(this);b.setText(AppUiPrefs.t(this,s));b.setAllCaps(false);b.setTypeface(null,Typeface.BOLD);b.setTextColor(PRIMARY_TEXT);b.setTextSize(12);b.setBackgroundColor(LIME);return b;}
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);} private float dpF(float v){return v*getResources().getDisplayMetrics().density;}
}
