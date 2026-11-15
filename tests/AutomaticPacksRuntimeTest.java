package com.unitepaintball.app;
import java.io.*;import java.nio.file.*;
/** Executes the production downloader/extractor on a Java runtime, not an Android phone. */
public class AutomaticPacksRuntimeTest{
 public static void main(String[] args)throws Exception{
  File root=Files.createTempDirectory("unite-auto-packs").toFile();
  String[][] packs={{"avatar","/downloads/avatar4k/avatar-4k-v1.zip","fb0f09a7c144aa154df96212b99e67a9956660c5f0a3994afd1d81a89c381ff5","148360892"},{"badges","/downloads/profile4k/UnitePaintball-BADGES-EXISTANTS-4K-v2.zip","0afa48fdad729e586531cb73728d0e1d53fdbe1cfa170d940814b142f61aa79e","31273949"}};
  for(String[] pack:packs){File target=new File(root,pack[0]);PackInstaller.install(AppConfig.BASE_URL+pack[1],pack[2],Long.parseLong(pack[3]),new File(root,pack[0]+".zip"),target,folder->{File manifest=new File(folder,"manifest.json");if(!manifest.isFile())throw new IOException("Missing manifest");String m=Files.readString(manifest.toPath()).replaceAll("\\s+","");if(!m.contains("\"resolution\":4096"))throw new IOException("Resolution invalid");long count;try(java.util.stream.Stream<Path> files=Files.walk(folder.toPath())){count=files.filter(Files::isRegularFile).count();}if(count<(pack[0].equals("avatar")?53:25))throw new IOException("Missing resources");},(state,done,total)->{});if(!Files.readString(new File(target,".verified").toPath()).equals(pack[2]))throw new AssertionError();if(new File(root,pack[0]+".zip").exists())throw new AssertionError();System.out.println("PASS: real HTTPS download, SHA256, extraction and activation: "+pack[0]);}
  PackInstaller.delete(root);
 }
}
