package uk.co.traynor.privategallery.core.media;
import java.io.*;
import java.util.*;
import uk.co.traynor.privategallery.core.hidden.HiddenRecordStore;
/** Independent encrypted recovery records, sealed with the existing originating vault key.
 * No key, PIN, session, authority or plaintext media is persisted. */
public final class ExternalEditRecoveryStore {
 private final String domain;private final File root;private final HiddenRecordStore records;
 public ExternalEditRecoveryStore(File parent,String domain) {
  if(!domain.equals("primary")&&!domain.equals("hidden"))throw new IllegalArgumentException("domain");
  this.domain=domain;root=new File(parent,"external-editor-"+domain+"-v1");records=new HiddenRecordStore(root);
 }
 public File file(String id){return records.file("metadata",id);}
 public List<String> ids(){List<String> out=new ArrayList<>();File[] files=root.listFiles();if(files!=null)for(File f:files)if(f.getName().matches("metadata-[a-f0-9-]{36}\\.enc"))out.add(f.getName().substring(9,45));Collections.sort(out);return out;}
 public static final class Receipt {
  public final String original,exportUri,resultUri,importedId;public final boolean saved;
  Receipt(String o,String e,String r,boolean s,String i){original=o;exportUri=e;resultUri=r;saved=s;importedId=i;}
 }
 public Receipt read(String id,byte[] key)throws Exception {
  byte[] plain=records.read("metadata",id,key);
  try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(plain))){if(in.readInt()!=1 || !in.readUTF().equals(domain))throw new IOException("Recovery domain/version");Receipt r=new Receipt(in.readUTF(),in.readUTF(),in.readUTF(),in.readBoolean(),in.readUTF());if(in.read()!=-1)throw new IOException("Recovery trailing data");return r;}finally{Arrays.fill(plain,(byte)0);}
 }
 public void write(String id,String original,String exportUri,String resultUri,boolean saved,byte[] key)throws Exception {
  write(id,original,exportUri,resultUri,saved,"",key);
 }
 public void write(String id,String original,String exportUri,String resultUri,boolean saved,String importedId,byte[] key)throws Exception {
  ByteArrayOutputStream out=new ByteArrayOutputStream();try(DataOutputStream data=new DataOutputStream(out)){data.writeInt(1);data.writeUTF(domain);data.writeUTF(original);data.writeUTF(exportUri);data.writeUTF(resultUri);data.writeBoolean(saved);data.writeUTF(importedId);}
  byte[] plain=out.toByteArray();File staged=null;
  try {staged=records.stage("metadata",id,plain,key);records.promote(staged,"metadata",id);records.syncPromotions();}finally{Arrays.fill(plain,(byte)0);out.reset();if(staged!=null)staged.delete();}
 }
 public void acknowledge(String id)throws IOException {if(file(id).exists()&&!file(id).delete())throw new IOException("Receipt cleanup failed");if(root.isDirectory())records.syncPromotions();}
}
