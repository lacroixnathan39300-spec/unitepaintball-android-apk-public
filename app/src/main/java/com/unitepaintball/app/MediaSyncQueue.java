package com.unitepaintball.app;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * File d'attente durable pour les médias créés dans l'application mais pas encore envoyés.
 *
 * Concurrence : STATE_LOCK ne protège que la lecture/écriture du fichier de métadonnées.
 * Les uploads et appels réseau se font hors verrou (voir SyncApi pour le raisonnement).
 */
public final class MediaSyncQueue {
    public static final String MEDIA_URL_PLACEHOLDER = "__UP_MEDIA_URL__";
    private static final String META = "pending-media-sync.json";
    private static final Object STATE_LOCK = new Object();
    private static final Object FLUSH_LOCK = new Object();
    /** Incrémenté à chaque effacement (déconnexion) : un envoi en cours n'y réécrit alors plus rien. */
    private static long epoch = 0L;
    private MediaSyncQueue() {}

    public static void enqueue(Context ctx, byte[] data, String fileName, String mime, String followPath, JSONObject followBody, String error) throws Exception {
        if(data==null || data.length==0) throw new IOException("Média vide");
        File dir=dir(ctx); if(!dir.exists()&&!dir.mkdirs())throw new IOException("Impossible de créer le cache média sortant");
        String id=UUID.randomUUID().toString();File bin=new File(dir,id+".bin");
        try(FileOutputStream out=new FileOutputStream(bin)){out.write(data);out.getFD().sync();}
        synchronized (STATE_LOCK) {
            JSONArray a=read(ctx);JSONObject x=new JSONObject();x.put("id",id);x.put("fileName",fileName==null?"media.bin":fileName);x.put("mime",mime==null?"application/octet-stream":mime);x.put("followPath",followPath);x.put("followBody",new JSONObject(followBody.toString()));x.put("createdAt",System.currentTimeMillis());x.put("retries",0);x.put("lastTry",0);x.put("lastError",error==null?"":error);x.put("blocked",false);a.put(x);write(ctx,a);
        }
    }

    public static int pendingCount(Context ctx){synchronized (STATE_LOCK){try{return read(ctx).length();}catch(Exception e){return 0;}}}
    public static int blockedCount(Context ctx){synchronized (STATE_LOCK){try{JSONArray a=read(ctx);int n=0;for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&x.optBoolean("blocked",false))n++;}return n;}catch(Exception e){return 0;}}}
    public static int retryBlocked(Context ctx){synchronized (STATE_LOCK){try{JSONArray a=read(ctx);int changed=0;for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&x.optBoolean("blocked",false)){x.put("blocked",false);x.put("retries",0);x.put("lastTry",0);x.put("lastError","");changed++;}}if(changed>0)write(ctx,a);return changed;}catch(Exception e){return 0;}}}

    public static int flush(Context ctx,String token,int max){
        if(token==null||token.isEmpty())return 0;
        synchronized (FLUSH_LOCK) {
            try{
                final long startEpoch;
                final JSONArray snapshot;
                synchronized (STATE_LOCK) {
                    startEpoch = epoch;
                    snapshot = read(ctx);
                }

                LinkedHashMap<String,String> originalById=new LinkedHashMap<>();
                LinkedHashMap<String,JSONObject> keptById=new LinkedHashMap<>();
                int sent=0,attempted=0;
                for(int i=0;i<snapshot.length();i++){
                    JSONObject item=snapshot.optJSONObject(i);if(item==null)continue;
                    String id=itemId(item,i);
                    originalById.put(id,item.toString());
                    if(attempted>=max||item.optBoolean("blocked",false)){keptById.put(id,item);continue;}
                    int retries=item.optInt("retries",0);long last=item.optLong("lastTry",0);long wait=Math.min(120000L,retries<=0?0L:2000L*(1L<<Math.min(6,retries)));
                    if(last>0&&System.currentTimeMillis()-last<wait){keptById.put(id,item);continue;}
                    attempted++;String fileId=item.optString("id","");File bin=new File(dir(ctx),fileId+".bin");
                    String persistedUrl=item.optString("uploadedUrl","");
                    if(persistedUrl.isEmpty()&&!bin.isFile()){item.put("blocked",true).put("lastError","Fichier local manquant");keptById.put(id,item);continue;}
                    try{
                        String url=persistedUrl;
                        if(url.isEmpty()){
                            byte[] bytes=readBytes(bin);
                            JSONObject uploaded=ApiClient.postMultipart("/api/media",token,bytes,item.optString("fileName","media.bin"),item.optString("mime","application/octet-stream"));
                            url=uploaded.optString("url","");if(url.isEmpty())throw new IOException("URL média absente");
                            // Sauvegarder immédiatement l'URL : si l'étape suivante échoue ou si l'application est tuée,
                            // le prochain essai reprend après l'upload au lieu de créer un média en double.
                            item.put("uploadedUrl",url).put("uploadedAt",System.currentTimeMillis());
                            persistUploadedUrl(ctx,fileId,url,item.optLong("uploadedAt",0));
                            // Le fichier contient désormais exactement cet état : ce n'est pas une modification concurrente.
                            originalById.put(id,item.toString());
                            if(bin.isFile()&&!bin.delete())bin.deleteOnExit();
                        }
                        JSONObject body=substitute(item.optJSONObject("followBody"),url);String path=item.optString("followPath","");if(path.isEmpty())throw new IOException("Destination média absente");ApiClient.post(path,token,body);sent++;
                    }catch(ApiClient.ApiException e){item.put("retries",retries+1).put("lastTry",System.currentTimeMillis()).put("lastError",safe(e.getMessage()));if(!(e.status==408||e.status==425||e.status==429||e.status>=500))item.put("blocked",true);keptById.put(id,item);
                    }catch(Exception e){item.put("retries",retries+1).put("lastTry",System.currentTimeMillis()).put("lastError",safe(e.getMessage()));keptById.put(id,item);}
                }

                // Fusion avec l'état actuel : ne pas écraser un média ajouté pendant l'envoi.
                synchronized (STATE_LOCK) {
                    if(epoch!=startEpoch)return sent;
                    JSONArray current=read(ctx),merged=new JSONArray();
                    for(int i=0;i<current.length();i++){
                        JSONObject cur=current.optJSONObject(i);if(cur==null)continue;
                        String id=itemId(cur,i);
                        if(!originalById.containsKey(id)){merged.put(cur);continue;}
                        if(!cur.toString().equals(originalById.get(id))){merged.put(cur);continue;}
                        JSONObject kept=keptById.get(id);
                        if(kept!=null)merged.put(kept);
                    }
                    write(ctx,merged);
                }
                return sent;
            }catch(Exception e){return 0;}
        }
    }

    private static String itemId(JSONObject item,int index){
        String id=item.optString("id","");
        return id.isEmpty()?"#"+index:id;
    }

    private static void persistUploadedUrl(Context ctx,String id,String url,long uploadedAt)throws Exception{
        if(id==null||id.isEmpty()||url==null||url.isEmpty())return;
        synchronized (STATE_LOCK) {
            JSONArray current=read(ctx);
            for(int i=0;i<current.length();i++){
                JSONObject x=current.optJSONObject(i);
                if(x!=null&&id.equals(x.optString("id",""))){
                    x.put("uploadedUrl",url);
                    x.put("uploadedAt",uploadedAt>0?uploadedAt:System.currentTimeMillis());
                    write(ctx,current);
                    return;
                }
            }
        }
    }

    private static JSONObject substitute(JSONObject src,String url)throws Exception{JSONObject out=new JSONObject(src==null?"{}":src.toString());java.util.Iterator<String> it=out.keys();while(it.hasNext()){String k=it.next();Object v=out.opt(k);if(v instanceof String&&MEDIA_URL_PLACEHOLDER.equals(v))out.put(k,url);else if(v instanceof JSONObject)out.put(k,substitute((JSONObject)v,url));else if(v instanceof JSONArray)out.put(k,substituteArray((JSONArray)v,url));}return out;}
    private static JSONArray substituteArray(JSONArray a,String url)throws Exception{JSONArray out=new JSONArray();for(int i=0;i<a.length();i++){Object v=a.opt(i);if(v instanceof String&&MEDIA_URL_PLACEHOLDER.equals(v))out.put(url);else if(v instanceof JSONObject)out.put(substitute((JSONObject)v,url));else if(v instanceof JSONArray)out.put(substituteArray((JSONArray)v,url));else out.put(v);}return out;}
    private static byte[] readBytes(File f)throws Exception{byte[] b=new byte[(int)f.length()];try(FileInputStream in=new FileInputStream(f)){int o=0,n;while(o<b.length&&(n=in.read(b,o,b.length-o))>0)o+=n;}return b;}
    private static String safe(String s){return s==null?"":s;}
    private static File dir(Context ctx){return new File(ctx.getFilesDir(),"pending_media");}
    private static File meta(Context ctx){return new File(ctx.getFilesDir(),META);}
    private static JSONArray read(Context ctx)throws Exception{File f=meta(ctx),bak=new File(ctx.getFilesDir(),META+".bak");try{return readArrayFile(f);}catch(Exception primary){if(bak.isFile()&&bak.length()>0){JSONArray recovered=readArrayFile(bak);try{write(ctx,recovered);}catch(Exception ignored){}return recovered;}if(!f.isFile()||f.length()==0)return new JSONArray();throw primary;}}
    private static JSONArray readArrayFile(File f)throws Exception{if(!f.isFile()||f.length()==0)return new JSONArray();return new JSONArray(new String(readBytes(f),StandardCharsets.UTF_8));}
    private static void write(Context ctx,JSONArray a)throws Exception{File f=meta(ctx),tmp=new File(ctx.getFilesDir(),META+".tmp"),bak=new File(ctx.getFilesDir(),META+".bak");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(a.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}if(bak.exists())bak.delete();boolean had=f.exists();if(had&&!f.renameTo(bak)){tmp.delete();throw new IOException("Impossible de sauvegarder la file média");}if(!tmp.renameTo(f)){if(had&&bak.exists())bak.renameTo(f);throw new IOException("Impossible de finaliser la file média");}if(bak.exists())bak.delete();}

    public static void clear(Context ctx){
        synchronized (STATE_LOCK) {
            epoch++;
            try{JSONArray a=read(ctx);for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null){File f=new File(dir(ctx),x.optString("id","")+".bin");if(f.exists())f.delete();}}File m=meta(ctx);if(m.exists())m.delete();File b=new File(ctx.getFilesDir(),META+".bak");if(b.exists())b.delete();File t=new File(ctx.getFilesDir(),META+".tmp");if(t.exists())t.delete();}catch(Exception ignored){}
        }
    }
}
