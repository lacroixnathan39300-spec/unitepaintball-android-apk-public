package com.unitepaintball.app;
import java.io.*;import java.nio.file.*;import java.util.zip.*;
public class PackInstallerTest {
 static int checks=0;
 static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static File zip(File root,String name,byte[] data)throws Exception{File f=new File(root,"test.zip");try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(f))){out.putNextEntry(new ZipEntry(name));out.write(data);out.closeEntry();}return f;}
 static void rejects(File f,File root,long limit,int files)throws Exception{boolean failed=false;try{PackInstaller.extract(f,root,limit,files);}catch(IOException e){failed=true;}ok(failed);}
 public static void main(String[] args)throws Exception{
  File root=Files.createTempDirectory("pack-test").toFile(),dest=new File(root,"install");dest.mkdirs();
  File good=zip(root,"theme/basecolor.jpg",new byte[]{1,2,3});PackInstaller.extract(good,dest,100,10);ok(new File(dest,"theme/basecolor.jpg").length()==3);
  rejects(zip(root,"../escape",new byte[]{1}),dest,100,10);ok(!new File(root,"escape").exists());
  rejects(zip(root,"/tmp/escape",new byte[]{1}),dest,100,0);
  rejects(zip(root,"large",new byte[100]),dest,10,10);
  rejects(zip(root,"one",new byte[]{1}),dest,100,0);
  Thread.currentThread().interrupt();rejects(zip(root,"interrupted",new byte[]{1}),dest,100,10);Thread.interrupted();
  PackInstaller.delete(root);ok(!root.exists());
  System.out.println("PASS: "+checks+" pack extraction/security assertions (Java runtime)");
 }
}
