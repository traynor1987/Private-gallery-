package uk.co.traynor.privategallery.core.media;
import java.io.*;import java.util.UUID;
/** Dedicated bounded disposable plaintext handoff; no vault identifier is placed in its name. */
public final class ExternalEditLease {
 public static final long LIFETIME=15*60*1000L;
 public static final int MAX_BYTES=64*1024*1024;
 private ExternalEditLease(){}
 public static File create(File root,byte[] bytes,long now)throws IOException {
  if(bytes.length==0||bytes.length>MAX_BYTES)throw new IllegalArgumentException("Image exceeds edit handoff limit");
  if(!root.isDirectory()&&!root.mkdirs()&&!root.isDirectory())throw new IOException("Edit staging unavailable");
  File file=new File(root,UUID.randomUUID()+".png");
  try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);out.getFD().sync();}
  catch(IOException failure){file.delete();throw failure;}
  if(!file.setLastModified(now)){file.delete();throw new IOException("Cannot establish edit lease");}return file;
 }
 public static boolean allowed(File f,long now){long age=now-f.lastModified();return f.isFile()&&f.getName().matches("[a-f0-9-]{36}\\.png")&&f.length()>0&&f.length()<=MAX_BYTES&&age>=0&&age<LIFETIME;}
 public static void cleanup(File root,long now){File[] files=root.listFiles();if(files==null)return;for(File f:files)if(f.getName().matches("[a-f0-9-]{36}\\.png")&&!allowed(f,now))f.delete();}
}
