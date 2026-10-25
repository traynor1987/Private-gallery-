import java.io.*;import java.nio.file.*;import java.util.*;
import uk.co.traynor.privategallery.core.media.ExternalEditLease;
public class ExternalEditLeaseChecks {
 static int n;static void check(boolean b){n++;if(!b)throw new AssertionError("check"+n);}
 public static void main(String[] args)throws Exception {
  File root=Files.createTempDirectory("external-edit").toFile();
  try {
   byte[]bytes={1,2,3};File f=ExternalEditLease.create(root,bytes,1000);
   check(f.getName().matches("[a-f0-9-]{36}\\.png"));check(Arrays.equals(bytes,Files.readAllBytes(f.toPath())));
   check(ExternalEditLease.allowed(f,1000));check(!ExternalEditLease.allowed(f,1000+ExternalEditLease.LIFETIME));
   File unrelated=new File(root,"keep.me");Files.writeString(unrelated.toPath(),"keep");
   ExternalEditLease.cleanup(root,1000+ExternalEditLease.LIFETIME);check(!f.exists());check(unrelated.exists());
   boolean rejected=false;try{ExternalEditLease.create(root,new byte[0],1000);}catch(IllegalArgumentException e){rejected=true;}check(rejected);
   File future=ExternalEditLease.create(root,bytes,3000);check(!ExternalEditLease.allowed(future,2000));
   System.out.println("External edit lease: "+n+" checks PASS");
  } finally {for(File f:root.listFiles())f.delete();root.delete();}
 }
}
