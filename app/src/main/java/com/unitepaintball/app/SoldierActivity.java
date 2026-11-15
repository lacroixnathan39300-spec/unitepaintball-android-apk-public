package com.unitepaintball.app;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.*;
import android.net.Uri;
import org.json.JSONObject;
import java.util.concurrent.*;
/** Offline packaged 3D renderer; restricted native API bridge never exposes session cookies. */
public final class SoldierActivity extends Activity {
 private volatile boolean destroyed=false; private WebView web; private SessionStore session; private Avatar4KPack pack4k; private final ExecutorService io=Executors.newSingleThreadExecutor();
 private String key(){return "soldier-"+Integer.toHexString(session.token().hashCode());}
 @Override public void onCreate(Bundle b){super.onCreate(b);session=new SessionStore(this);pack4k=new Avatar4KPack(this);web=new WebView(this);web.setBackgroundColor(NeonPalette.BG);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.addJavascriptInterface(new Bridge(),"AndroidSoldier");web.setWebViewClient(new WebViewClient(){
 @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
 @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest r){Uri u=r.getUrl();String p=u.getPath();if(!"appassets.androidplatform.net".equals(u.getHost())||p==null||p.contains(".."))return new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));try{if(p.startsWith("/avatar4k/")){String rel=p.substring(10);String mime=p.endsWith(".jpg")?"image/jpeg":p.endsWith(".png")?"image/png":"application/json";return new WebResourceResponse(mime,null,pack4k.open(rel));}if(!p.startsWith("/assets/"))throw new java.io.FileNotFoundException();String mime=p.endsWith(".js")?"application/javascript":p.endsWith(".glb")?"model/gltf-binary":p.endsWith(".svg")?"image/svg+xml":"text/html";return new WebResourceResponse(mime,p.endsWith(".glb")?null:"UTF-8",getAssets().open(p.substring(8)));}catch(Exception e){return new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));}}
 });AppUiPrefs.applyWindow(this);AppUiPrefs.applySafeArea(web);setContentView(web);web.loadUrl("https://appassets.androidplatform.net/assets/soldier/index.html");}
 public final class Bridge {
 @JavascriptInterface public void close(){runOnUiThread(()->finish());}
 @JavascriptInterface public void request(int id,String action,String payload){io.execute(()->{JSONObject d;try{if(action.equals("load")){try{d=ApiClient.get("/api/avatar/soldier",session.token());getSharedPreferences("avatar-cache",MODE_PRIVATE).edit().putString(key(),d.toString()).apply();}catch(ApiClient.ApiException e){if(e.status==401||e.status==403)throw e;d=cached();}catch(Exception e){d=cached();}}else if(action.equals("save")){d=ApiClient.post("/api/avatar/soldier",session.token(),new JSONObject(payload));}else if(action.equals("badges")){d=ApiClient.get("/api/gamification?scope=profile",session.token());}else if(action.equals("graphicsStatus")){d=pack4k.status();}else if(action.equals("install4k")){d=pack4k.install((state,done,total)->runOnUiThread(()->{if(!isFinishing()&&!destroyed)web.evaluateJavascript("window.packProgress("+JSONObject.quote(state)+","+done+","+total+")",null);}));}else if(action.equals("remove4k")){d=pack4k.remove();}else throw new Exception("Action interdite.");}catch(Exception e){d=new JSONObject();try{d.put("error",e.getMessage());}catch(Exception ignored){}}final String result=d.toString();runOnUiThread(()->{if(!isFinishing()&&!destroyed)web.evaluateJavascript("window.receive("+id+","+result+")",null);});});}
 private JSONObject cached()throws Exception{String s=getSharedPreferences("avatar-cache",MODE_PRIVATE).getString(key(),"");if(s.isEmpty())throw new Exception("Connexion requise : aucun inventaire local.");return new JSONObject(s).put("_offline",true);}
 }
 @Override protected void onDestroy(){destroyed=true;io.shutdownNow();web.removeJavascriptInterface("AndroidSoldier");web.destroy();super.onDestroy();}
}
