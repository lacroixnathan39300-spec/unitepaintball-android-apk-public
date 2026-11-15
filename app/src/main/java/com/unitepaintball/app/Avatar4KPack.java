package com.unitepaintball.app;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Optional Android-only 4K PBR costume pack. Ownership remains server-side. */
public final class Avatar4KPack {
 public static final String ID="avatar-4k-v1";
 public static final String URL=AppConfig.BASE_URL+"/downloads/avatar4k/avatar-4k-v1.zip";
 public static final String SHA256="fb0f09a7c144aa154df96212b99e67a9956660c5f0a3994afd1d81a89c381ff5";
 public static final long BYTES=148360892L; // informational; checksum is authoritative
 private final Context c;
 public Avatar4KPack(Context c){this.c=c.getApplicationContext();}
 public File dir(){return new File(c.getFilesDir(),"avatar4k/"+ID);}
 public boolean installed(){return new File(dir(),"manifest.json").isFile()&&new File(dir(),".verified").isFile();}
 public JSONObject status() throws Exception {return new JSONObject().put("installed",installed()).put("id",ID).put("bytes",BYTES).put("url",URL).put("resolution",4096);}
 public InputStream open(String relative) throws IOException {if(relative==null||relative.contains("..")||relative.startsWith("/"))throw new FileNotFoundException();File f=new File(dir(),relative);String base=dir().getCanonicalPath()+File.separator;if(!f.getCanonicalPath().startsWith(base)||!f.isFile())throw new FileNotFoundException();return new FileInputStream(f);}
 public JSONObject install()throws Exception{return install((state,done,total)->{});}
 public JSONObject install(PackInstaller.Progress progress)throws Exception{
  PackInstaller.install(URL,SHA256,BYTES,new File(c.getCacheDir(),ID+".zip"),dir(),tmp->{JSONObject m=new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(tmp,"manifest.json").toPath()),java.nio.charset.StandardCharsets.UTF_8));
   if(!ID.equals(m.optString("id"))||m.optInt("resolution")!=4096)throw new IOException("Manifest incorrect.");org.json.JSONArray themes=m.getJSONArray("themes");if(themes.length()!=13)throw new IOException("Costumes incomplets.");for(int i=0;i<themes.length();i++){String theme=themes.getJSONObject(i).getString("id");if(!theme.matches("[a-z0-9-]+"))throw new IOException("Costume invalide.");for(String map:new String[]{"basecolor.jpg","normal.jpg","roughness.jpg","metallic.jpg"})if(!new File(tmp,theme+"/"+map).isFile())throw new IOException("Texture absente.");}},progress);return status();
 }
 public JSONObject remove()throws Exception{synchronized(PackInstaller.LOCK){PackInstaller.delete(dir());return status();}}
}
