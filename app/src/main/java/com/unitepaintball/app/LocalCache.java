package com.unitepaintball.app;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Iterator;
import org.json.JSONArray;

/** Cache persistant disque : réponses JSON API + médias. */
public final class LocalCache {
    private final File jsonDir;
    private final File mediaDir;

    public LocalCache(Context context) {
        File root = new File(context.getFilesDir(), "offline-cache-v2");
        jsonDir = new File(root, "json");
        mediaDir = new File(root, "media");
        jsonDir.mkdirs();
        mediaDir.mkdirs();
    }

    public synchronized JSONObject getJson(String key) {
        try {
            File f = new File(jsonDir, sha(key) + ".json");
            if (!f.isFile()) return null;
            // Une donnée réellement relue hors ligne est considérée comme utile : on la garde plus longtemps.
            f.setLastModified(System.currentTimeMillis());
            return new JSONObject(readText(f));
        } catch (Exception e) { return null; }
    }

    public synchronized String getJsonText(String key) {
        try {
            File f = new File(jsonDir, sha(key) + ".json");
            if (!f.isFile()) return "";
            f.setLastModified(System.currentTimeMillis());
            return readText(f);
        } catch (Exception e) { return ""; }
    }

    public synchronized void putJson(String key, JSONObject value) {
        if (value == null) return;
        try {
            File f = new File(jsonDir, sha(key) + ".json");
            writeAtomic(f, value.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    public synchronized void remove(String key) {
        try {
            File f = new File(jsonDir, sha(key) + ".json");
            if (f.isFile()) f.delete();
        } catch (Exception ignored) {}
    }

    public File mediaFile(String mediaPath) {
        try {
            String ext = ".bin";
            String p = mediaPath == null ? "" : mediaPath.toLowerCase();
            if (p.contains(".jpg") || p.contains(".jpeg")) ext = ".jpg";
            else if (p.contains(".png")) ext = ".png";
            else if (p.contains(".webp")) ext = ".webp";
            else if (p.contains(".gif")) ext = ".gif";
            else if (p.contains(".mp4")) ext = ".mp4";
            else if (p.contains(".webm")) ext = ".webm";
            else if (p.contains(".mov")) ext = ".mov";
            else if (p.contains(".m4v")) ext = ".m4v";
            return new File(mediaDir, sha(String.valueOf(mediaPath)) + ext);
        } catch (Exception e) { return new File(mediaDir, "invalid.bin"); }
    }

    public boolean hasMedia(String mediaPath) {
        File f = mediaFile(mediaPath);
        return f.isFile() && f.length() > 0;
    }

    public File downloadMedia(String mediaPath, String token) throws Exception {
        File target = mediaFile(mediaPath);
        if (target.isFile() && target.length() > 0) {
            target.setLastModified(System.currentTimeMillis());
            return target;
        }
        String full = mediaPath.startsWith("http") ? mediaPath : AppConfig.BASE_URL + mediaPath;
        HttpURLConnection c = (HttpURLConnection) new URL(full).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent", "UnitePaintball-Android/" + AppConfig.APP_VERSION);
        if (token != null && !token.isEmpty()) c.setRequestProperty("Cookie", token);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[16384]; int n; long total = 0;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > 80L * 1024L * 1024L) throw new IOException("Média trop volumineux");
                out.write(buf, 0, n);
            }
        }
        if (!tmp.renameTo(target)) {
            copyFile(tmp, target); tmp.delete();
        }
        return target;
    }

    /** Précharge toutes les URLs de médias trouvées dans une réponse JSON. */
    public int prefetchMedia(JSONObject root, String token, int max) {
        java.util.LinkedHashSet<String> urls = new java.util.LinkedHashSet<>();
        collectMedia(root, urls);
        int done = 0;
        for (String u : urls) {
            if (done >= max) break;
            try { downloadMedia(u, token); done++; } catch (Exception ignored) {}
        }
        return done;
    }

    private void collectMedia(Object value, java.util.Set<String> out) {
        if (value instanceof JSONObject) {
            JSONObject o = (JSONObject) value;
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next(); Object v = o.opt(k);
                if (v instanceof String) {
                    String s = (String) v; String lk = k.toLowerCase();
                    if ((lk.contains("url") || lk.contains("photo") || lk.contains("image") || lk.contains("avatar") || lk.contains("media")) &&
                            (s.startsWith("http") || s.startsWith("/"))) out.add(s);
                } else collectMedia(v, out);
            }
        } else if (value instanceof JSONArray) {
            JSONArray a = (JSONArray) value;
            for (int i=0;i<a.length();i++) collectMedia(a.opt(i), out);
        }
    }


    /**
     * Entretien prudent du cache hors ligne.
     * - supprime les fichiers temporaires abandonnés ;
     * - garde les réponses JSON récentes ;
     * - limite seulement les médias les plus anciens si le cache devient trop gros.
     */
    public synchronized void maintain(long maxMediaBytes, long maxJsonAgeMs) {
        long now = System.currentTimeMillis();
        deleteTmpFiles(jsonDir);
        deleteTmpFiles(mediaDir);

        File[] json = jsonDir.listFiles();
        if (json != null && maxJsonAgeMs > 0) {
            for (File f : json) {
                if (f.isFile() && now - f.lastModified() > maxJsonAgeMs) f.delete();
            }
        }

        if (maxMediaBytes <= 0) return;
        File[] media = mediaDir.listFiles();
        if (media == null || media.length == 0) return;
        java.util.Arrays.sort(media, (a,b) -> Long.compare(a.lastModified(), b.lastModified()));
        long total = 0;
        for (File f : media) if (f.isFile()) total += f.length();
        for (File f : media) {
            if (total <= maxMediaBytes) break;
            if (!f.isFile()) continue;
            long len = f.length();
            if (f.delete()) total -= len;
        }
    }

    public synchronized long jsonSizeBytes() { return dirSize(jsonDir); }
    public synchronized long mediaSizeBytes() { return dirSize(mediaDir); }

    private static void deleteTmpFiles(File dir) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) if (f.isFile() && f.getName().endsWith(".tmp")) f.delete();
    }

    public long sizeBytes() { return dirSize(jsonDir) + dirSize(mediaDir); }
    public synchronized void clear(){deleteChildren(jsonDir);deleteChildren(mediaDir);}
    private static void deleteChildren(File d){File[] fs=d.listFiles();if(fs!=null)for(File f:fs){if(f.isDirectory())deleteChildren(f);f.delete();}}

    private static long dirSize(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long n=0; File[] files=f.listFiles(); if(files!=null) for(File x:files)n+=dirSize(x); return n;
    }
    private static String sha(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] b = md.digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(); for(byte x:b) sb.append(String.format("%02x", x)); return sb.toString();
    }
    private static String readText(File f) throws Exception {
        try(InputStream in=new FileInputStream(f); ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");
        }
    }
    private static void writeAtomic(File f, byte[] data) throws Exception {
        File tmp=new File(f.getParentFile(),f.getName()+".tmp");
        try(OutputStream out=new FileOutputStream(tmp)){out.write(data);out.flush();}
        if(!tmp.renameTo(f)){copyFile(tmp,f);tmp.delete();}
    }
    private static void copyFile(File a,File b)throws Exception{
        try(InputStream in=new FileInputStream(a);OutputStream out=new FileOutputStream(b)){byte[] x=new byte[8192];int n;while((n=in.read(x))!=-1)out.write(x,0,n);}
    }
}
