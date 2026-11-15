package com.unitepaintball.app;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Bounded, verified and transactional optional-pack installation. */
final class PackInstaller {
 interface Progress { void update(String state,long done,long total); }
 interface Validator { void validate(File root) throws Exception; }
 static final Object LOCK=new Object();
 static void install(String url,String hash,long bytes,File zip,File target,Validator validator,Progress progress)throws Exception{
  synchronized(LOCK){
   File parent=target.getParentFile(),tmp=new File(parent,target.getName()+".tmp"),backup=new File(parent,target.getName()+".previous");
   if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Espace privé indisponible.");
   if(!target.exists()&&backup.exists()&&!backup.renameTo(target))throw new IOException("Restauration du pack impossible.");
   delete(tmp);if(!tmp.mkdirs())throw new IOException("Installation indisponible.");
   HttpURLConnection h=null;
   try{
    URL endpoint=new URL(url);
    if(!"https".equals(endpoint.getProtocol()))throw new IOException("Le pack exige HTTPS.");
    h=(HttpURLConnection)endpoint.openConnection();h.setInstanceFollowRedirects(false);h.setConnectTimeout(15000);h.setReadTimeout(120000);h.setRequestProperty("Accept","application/zip");h.setRequestProperty("User-Agent","UnitePaintball-Android/"+AppConfig.APP_VERSION);
    if(h.getResponseCode()!=200)throw new IOException("Pack indisponible (HTTP "+h.getResponseCode()+").");
    long length=h.getContentLengthLong();if(length>=0&&length!=bytes)throw new IOException("Taille du pack incorrecte.");
    progress.update("Téléchargement",0,bytes);
    try(InputStream in=h.getInputStream();OutputStream out=new BufferedOutputStream(new FileOutputStream(zip))){byte[] b=new byte[65536];long nbytes=0,last=0;for(int n;(n=in.read(b))!=-1;){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();nbytes+=n;if(nbytes>bytes)throw new IOException("Pack trop volumineux.");out.write(b,0,n);if(nbytes-last>=1024*1024){progress.update("Téléchargement",nbytes,bytes);last=nbytes;}}if(nbytes!=bytes)throw new IOException("Téléchargement incomplet.");}
    progress.update("Vérification",bytes,bytes);
    if(!hash.equalsIgnoreCase(sha256(zip)))throw new IOException("Contrôle SHA-256 échoué.");
    progress.update("Installation",0,bytes);
    extract(zip,tmp,Math.max(bytes*3,1024*1024),128);
    validator.validate(tmp);
    try(Writer w=new FileWriter(new File(tmp,".verified"))){w.write(hash);}
    delete(backup);
    if(target.exists()&&!target.renameTo(backup))throw new IOException("Sauvegarde du pack impossible.");
    if(!tmp.renameTo(target)){if(backup.exists())backup.renameTo(target);throw new IOException("Activation impossible.");}
    delete(backup);progress.update("Activé",bytes,bytes);
   }catch(Exception e){progress.update("Erreur",0,bytes);throw e;}
   finally{if(h!=null)h.disconnect();delete(zip);delete(tmp);}
  }
 }
 static void extract(File zip,File root,long limit,int maxFiles)throws IOException{
  String base=root.getCanonicalPath()+File.separator;long total=0;Set<String> names=new HashSet<>();
  try(ZipInputStream in=new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))){
   for(ZipEntry e;(e=in.getNextEntry())!=null;){
    if(e.isDirectory())continue;
    File f=new File(root,e.getName());String canonical=f.getCanonicalPath();
    if(!canonical.startsWith(base)||!names.add(canonical)||names.size()>maxFiles)throw new IOException("Archive invalide.");
    File p=f.getParentFile();if(!p.isDirectory()&&!p.mkdirs())throw new IOException("Extraction impossible.");
    try(OutputStream out=new BufferedOutputStream(new FileOutputStream(f))){byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();total+=n;if(total>limit)throw new IOException("Archive trop volumineuse.");out.write(b,0,n);}}
   }
  }
 }
 static void delete(File f)throws IOException{if(f==null||!f.exists())return;if(f.isDirectory()){File[] xs=f.listFiles();if(xs==null)throw new IOException("Dossier illisible.");for(File x:xs)delete(x);}if(!f.delete())throw new IOException("Suppression impossible.");}
 static String sha256(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[]b=new byte[65536];for(int n;(n=in.read(b))!=-1;)d.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte x:d.digest())s.append(String.format("%02x",x));return s.toString();}
}
