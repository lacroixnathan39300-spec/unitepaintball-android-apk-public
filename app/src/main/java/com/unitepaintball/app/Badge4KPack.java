package com.unitepaintball.app;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Optional Android 4K pack for the EXISTING badge IDs. */
public final class Badge4KPack {
 public static final String ID="profile-badges-4k-v2";
 public static final String URL=AppConfig.BASE_URL+"/downloads/profile4k/UnitePaintball-BADGES-EXISTANTS-4K-v2.zip";
 public static final String SHA256="0afa48fdad729e586531cb73728d0e1d53fdbe1cfa170d940814b142f61aa79e";
 public static final long BYTES=31273949L;
 private final Context c; public Badge4KPack(Context c){this.c=c.getApplicationContext();}
 public File dir(){return new File(c.getFilesDir(),"profile4k/"+ID);}
 public boolean installed(){return new File(dir(),"manifest.json").isFile()&&new File(dir(),".verified").isFile();}
 public InputStream openBadge(String id)throws IOException{if(id==null||!id.matches("[a-zA-Z0-9_-]+"))throw new FileNotFoundException();File f=new File(dir(),"badges/"+id+".png");if(!f.isFile())throw new FileNotFoundException();return new FileInputStream(f);}
 public JSONObject status()throws Exception{return new JSONObject().put("installed",installed()).put("id",ID).put("bytes",BYTES).put("resolution",4096).put("url",URL);}
 public JSONObject install()throws Exception{return install((state,done,total)->{});}
 public JSONObject install(PackInstaller.Progress progress)throws Exception{
  PackInstaller.install(URL,SHA256,BYTES,new File(c.getCacheDir(),ID+".zip"),dir(),tmp->{JSONObject m=new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(tmp,"manifest.json").toPath()),java.nio.charset.StandardCharsets.UTF_8));
   if(!ID.equals(m.optString("id"))||m.optInt("resolution")!=4096)throw new IOException("Manifest incorrect.");org.json.JSONArray badges=m.getJSONArray("badges");if(badges.length()!=24)throw new IOException("Badges incomplets.");for(int i=0;i<badges.length();i++){JSONObject badge=badges.getJSONObject(i);String id=badge.getString("id");if(!id.matches("[a-zA-Z0-9_-]+"))throw new IOException("Badge invalide.");File image=new File(tmp,"badges/"+id+".png");if(!image.isFile()||!badge.getString("sha256").equalsIgnoreCase(PackInstaller.sha256(image)))throw new IOException("Badge incorrect.");}},progress);return status();
 }
 public JSONObject remove()throws Exception{synchronized(PackInstaller.LOCK){PackInstaller.delete(dir());return status();}}
}
