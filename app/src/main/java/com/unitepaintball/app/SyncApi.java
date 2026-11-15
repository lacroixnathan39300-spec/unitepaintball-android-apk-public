package com.unitepaintball.app;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * Couche d'écriture bidirectionnelle.
 * - en ligne : l'action est envoyée immédiatement au site ;
 * - coupure réseau / 5xx / 408 / 425 / 429 : l'action est conservée dans une file privée ;
 * - 4xx métier : l'erreur est renvoyée à l'écran, elle n'est pas rejouée aveuglément.
 *
 * Concurrence : STATE_LOCK ne protège que la lecture/écriture du fichier (quelques ms).
 * Aucun appel réseau n'est fait en le tenant, pour que l'UI et les nouvelles actions hors ligne
 * ne soient jamais bloquées par un envoi en cours. FLUSH_LOCK empêche seulement deux envois simultanés
 * de rejouer la même action deux fois.
 */
public final class SyncApi {
    private static final String FILE_NAME = "pending-site-mutations.json";
    private static final Object STATE_LOCK = new Object();
    private static final Object FLUSH_LOCK = new Object();
    /** Incrémenté à chaque effacement (déconnexion) : un envoi en cours n'y réécrit alors plus rien. */
    private static long epoch = 0L;
    private SyncApi() {}

    public static JSONObject post(Context ctx, String path, String token, JSONObject body) throws Exception {
        return mutate(ctx,"POST",path,token,body);
    }
    public static JSONObject patch(Context ctx, String path, String token, JSONObject body) throws Exception {
        return mutate(ctx,"PATCH",path,token,body);
    }

    private static JSONObject mutate(Context ctx,String method,String path,String token,JSONObject body) throws Exception {
        try {
            return "PATCH".equals(method) ? ApiClient.patch(path,token,body) : ApiClient.post(path,token,body);
        } catch (ApiClient.ApiException e) {
            if (isRetryable(e.status)) {
                enqueue(ctx,method,path,body,e.getMessage());
                return queuedResult();
            }
            throw e;
        } catch (Exception e) {
            enqueue(ctx,method,path,body,e.getMessage());
            return queuedResult();
        }
    }

    private static boolean isRetryable(int status){
        return status==408 || status==425 || status==429 || status>=500;
    }

    private static JSONObject queuedResult() throws Exception {
        return new JSONObject().put("_queued",true).put("ok",true).put("message","Enregistré sur le téléphone · envoi au site en attente");
    }

    public static int pendingCount(Context ctx){
        synchronized (STATE_LOCK) {
            try { return read(ctx).length(); } catch(Exception e){ return 0; }
        }
    }
    public static int blockedCount(Context ctx){
        synchronized (STATE_LOCK) {
            try { JSONArray a=read(ctx);int n=0;for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&x.optBoolean("blocked",false))n++;}return n; } catch(Exception e){ return 0; }
        }
    }

    public static int flush(Context ctx,String token,int max) {
        if(token==null || token.isEmpty()) return 0;
        synchronized (FLUSH_LOCK) {
            try {
                final long startEpoch;
                final JSONArray snapshot;
                synchronized (STATE_LOCK) {
                    startEpoch = epoch;
                    snapshot = read(ctx);
                }

                // Clé = id ; valeur d'origine (sérialisée) pour détecter ce qui a changé pendant l'envoi.
                LinkedHashMap<String,String> originalById = new LinkedHashMap<>();
                // Éléments à conserver après l'envoi (les éléments envoyés n'y figurent pas).
                LinkedHashMap<String,JSONObject> keptById = new LinkedHashMap<>();
                int sent=0, attempted=0;

                for(int i=0;i<snapshot.length();i++){
                    JSONObject item=snapshot.optJSONObject(i); if(item==null)continue;
                    String id=itemId(item,i);
                    originalById.put(id,item.toString());
                    if(attempted>=max){keptById.put(id,item);continue;}
                    if(item.optBoolean("blocked",false)){keptById.put(id,item);continue;}
                    long lastTry=item.optLong("lastTry",0);int retries=item.optInt("retries",0);long backoff=Math.min(60000L, retries<=0?0L:(1000L*(1L<<Math.min(6,retries))));
                    if(lastTry>0 && System.currentTimeMillis()-lastTry<backoff){keptById.put(id,item);continue;}
                    attempted++;
                    try{
                        String method=item.optString("method","POST"); String path=item.optString("path","");
                        JSONObject body=item.optJSONObject("body"); if(body==null)body=new JSONObject();
                        if("PATCH".equals(method))ApiClient.patch(path,token,body); else ApiClient.post(path,token,body);
                        sent++;
                    }catch(ApiClient.ApiException e){
                        item.put("retries",item.optInt("retries",0)+1); item.put("lastError",safe(e.getMessage())); item.put("lastTry",System.currentTimeMillis());
                        if(!isRetryable(e.status)) item.put("blocked",true);
                        keptById.put(id,item);
                    }catch(Exception e){
                        item.put("retries",item.optInt("retries",0)+1); item.put("lastError",safe(e.getMessage())); item.put("lastTry",System.currentTimeMillis());
                        keptById.put(id,item);
                    }
                }

                // Fusion avec l'état actuel du fichier : ne jamais écraser une action créée pendant l'envoi.
                synchronized (STATE_LOCK) {
                    if(epoch!=startEpoch) return sent; // file effacée entre-temps (déconnexion) : ne rien réécrire
                    JSONArray current=read(ctx), merged=new JSONArray();
                    for(int i=0;i<current.length();i++){
                        JSONObject cur=current.optJSONObject(i); if(cur==null)continue;
                        String id=itemId(cur,i);
                        if(!originalById.containsKey(id)){ merged.put(cur); continue; }      // ajoutée pendant l'envoi
                        if(!cur.toString().equals(originalById.get(id))){ merged.put(cur); continue; } // modifiée pendant l'envoi : la version récente gagne
                        JSONObject kept=keptById.get(id);
                        if(kept!=null) merged.put(kept);                                     // à réessayer
                        // sinon : envoyée avec succès, on la retire
                    }
                    write(ctx,merged);
                }
                return sent;
            } catch(Exception e){ return 0; }
        }
    }

    private static String itemId(JSONObject item,int index){
        String id=item.optString("id","");
        return id.isEmpty()?"#"+index:id;
    }

    public static JSONArray snapshot(Context ctx){
        synchronized (STATE_LOCK) {
            try{return new JSONArray(read(ctx).toString());}catch(Exception e){return new JSONArray();}
        }
    }

    /** Réactive les écritures bloquées après correction côté serveur ou données. */
    public static int retryBlocked(Context ctx){
        synchronized (STATE_LOCK) {
            try{
                JSONArray a=read(ctx);int changed=0;
                for(int i=0;i<a.length();i++){
                    JSONObject x=a.optJSONObject(i);
                    if(x!=null&&x.optBoolean("blocked",false)){
                        x.put("blocked",false);x.put("retries",0);x.put("lastTry",0);x.put("lastError","");changed++;
                    }
                }
                if(changed>0)write(ctx,a);
                return changed;
            }catch(Exception e){return 0;}
        }
    }

    public static void clear(Context ctx){
        synchronized (STATE_LOCK) {
            epoch++;
            try{File f=file(ctx);if(f.exists())f.delete();File b=new File(ctx.getFilesDir(),FILE_NAME+".bak");if(b.exists())b.delete();File t=new File(ctx.getFilesDir(),FILE_NAME+".tmp");if(t.exists())t.delete();}catch(Exception ignored){}
        }
    }

    private static void enqueue(Context ctx,String method,String path,JSONObject body,String error)throws Exception{
        synchronized (STATE_LOCK) {
            JSONArray a=read(ctx); JSONObject payload=new JSONObject(body==null?"{}":body.toString()); String key=dedupeKey(method,path,payload);
            if(!key.isEmpty()){
                for(int x=0;x<a.length();x++){JSONObject old=a.optJSONObject(x);if(old!=null&&key.equals(old.optString("dedupeKey",""))){old.put("body",payload);old.put("lastError",safe(error));old.put("blocked",false);old.put("lastTry",0);write(ctx,a);return;}}
            }
            JSONObject i=new JSONObject();i.put("id", UUID.randomUUID().toString()); i.put("method",method); i.put("path",path);i.put("body",payload); i.put("createdAt",System.currentTimeMillis());i.put("retries",0); i.put("lastError",safe(error)); i.put("blocked",false);if(!key.isEmpty())i.put("dedupeKey",key);a.put(i);write(ctx,a);
        }
    }
    private static String dedupeKey(String method,String path,JSONObject body){
        String action=body.optString("action","");
        if("read_channel".equals(action))return method+"|"+path+"|read_channel|"+body.optString("channel","");
        if("read_notifications".equals(action))return method+"|"+path+"|read_notifications";
        if("read_notification".equals(action))return method+"|"+path+"|read_notification|"+body.optString("notificationId","");
        if("save_profile".equals(action))return method+"|"+path+"|save_profile";
        if("session_status".equals(action))return method+"|"+path+"|session_status|"+body.optString("sessionId","");
        if("team_score".equals(action))return method+"|"+path+"|team_score|"+body.optString("teamId","");
        return "";
    }
    private static String safe(String s){return s==null?"":s;}
    private static File file(Context ctx){return new File(ctx.getFilesDir(),FILE_NAME);}
    private static JSONArray read(Context ctx)throws Exception{
        File f=file(ctx),bak=new File(ctx.getFilesDir(),FILE_NAME+".bak");
        try{return readArrayFile(f);}catch(Exception primary){
            if(bak.isFile()&&bak.length()>0){
                JSONArray recovered=readArrayFile(bak);
                try{write(ctx,recovered);}catch(Exception ignored){}
                return recovered;
            }
            if(!f.isFile()||f.length()==0)return new JSONArray();
            throw primary;
        }
    }
    private static JSONArray readArrayFile(File f)throws Exception{
        if(!f.isFile()||f.length()==0)return new JSONArray();
        byte[] b=new byte[(int)f.length()];try(FileInputStream in=new FileInputStream(f)){int off=0,n;while(off<b.length&&(n=in.read(b,off,b.length-off))>0)off+=n;}
        String s=new String(b,StandardCharsets.UTF_8).trim();return s.isEmpty()?new JSONArray():new JSONArray(s);
    }
    private static void write(Context ctx,JSONArray a)throws Exception{
        File f=file(ctx),tmp=new File(ctx.getFilesDir(),FILE_NAME+".tmp"),bak=new File(ctx.getFilesDir(),FILE_NAME+".bak");
        try(FileOutputStream out=new FileOutputStream(tmp)){out.write(a.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
        if(bak.exists())bak.delete();
        boolean had=f.exists();
        if(had&&!f.renameTo(bak)){tmp.delete();throw new java.io.IOException("Impossible de sauvegarder la file de synchronisation");}
        if(!tmp.renameTo(f)){
            if(had&&bak.exists())bak.renameTo(f);
            throw new java.io.IOException("Impossible de finaliser la file de synchronisation");
        }
        if(bak.exists())bak.delete();
    }
}
