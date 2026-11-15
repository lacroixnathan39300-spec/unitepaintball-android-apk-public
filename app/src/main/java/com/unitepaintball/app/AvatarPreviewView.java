package com.unitepaintball.app;
import android.app.Activity;
import android.webkit.*;
import android.net.Uri;
import org.json.JSONObject;
/** Offline packaged read-only 3D preview. Receives only the social avatar DTO. */
public final class AvatarPreviewView extends WebView {
 private boolean disposed=false;
 public AvatarPreviewView(Activity activity,JSONObject config){super(activity);Avatar4KPack pack=new Avatar4KPack(activity);setBackgroundColor(NeonPalette.SURFACE);getSettings().setJavaScriptEnabled(true);getSettings().setAllowFileAccess(false);getSettings().setAllowContentAccess(false);getSettings().setDomStorageEnabled(true);
 addJavascriptInterface(new Object(){@JavascriptInterface public void close(){}@JavascriptInterface public void request(int id,String action,String payload){JSONObject data=new JSONObject();try{if("load".equals(action)){data.put("config",config).put("version",0).put("collection",new org.json.JSONArray()).put("_offline",true);}else if("graphicsStatus".equals(action))data=pack.status();else data.put("error","Aperçu en lecture seule.");}catch(Exception e){try{data.put("error","Avatar indisponible.");}catch(Exception ignored){}}String response=data.toString();activity.runOnUiThread(()->{if(!disposed&&!activity.isFinishing())evaluateJavascript("window.receive("+id+","+response+")",null);});}},"AndroidSoldier");
 setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){return true;}@Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){Uri u=request.getUrl();String path=u.getPath();try{if(!"appassets.androidplatform.net".equals(u.getHost())||path==null||path.contains(".."))throw new java.io.IOException();if(path.startsWith("/avatar4k/"))return new WebResourceResponse(path.endsWith(".jpg")?"image/jpeg":"application/json",null,pack.open(path.substring(10)));if(!path.startsWith("/assets/"))throw new java.io.IOException();String mime=path.endsWith(".js")?"application/javascript":path.endsWith(".glb")?"model/gltf-binary":path.endsWith(".svg")?"image/svg+xml":path.endsWith(".png")?"image/png":"text/html";return new WebResourceResponse(mime,path.endsWith(".glb")||path.endsWith(".png")?null:"UTF-8",getContext().getAssets().open(path.substring(8)));}catch(Exception e){return new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));}}});loadUrl("https://appassets.androidplatform.net/assets/soldier/index.html?readonly=1");
 }
 public void dispose(){disposed=true;removeJavascriptInterface("AndroidSoldier");stopLoading();destroy();}
}
