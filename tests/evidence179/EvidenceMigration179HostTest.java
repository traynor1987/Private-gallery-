package uk.co.traynor.privategallery.core.evidence;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import uk.co.traynor.privategallery.core.evidence.EvidenceCatalog.*;
import uk.co.traynor.privategallery.core.hidden.HiddenRecordStore;

/** Near-limit local v1 migration through the real bounded codec and encrypted store. */
public final class EvidenceMigration179HostTest {
 static int passed;
 static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
 static void check(boolean value){if(!value)throw new AssertionError();passed++;}
 static void reject(Runnable action){try{action.run();throw new AssertionError("invalid migration accepted");}catch(IllegalArgumentException expected){passed++;}}
 static void string(DataOutputStream out,String text)throws IOException{byte[] bytes=text.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
 // Exact independent v1 fields: no new codec writer or size-quota bypass constructs this fixture.
 static byte[] legacy(Owner owner,boolean changedSnapshot)throws IOException{
  var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
  out.writeInt(0x50474543);out.writeInt(1);string(out,owner.name());out.writeLong(changedSnapshot?1:0);out.writeInt(64);
  for(int n=1;n<=64;n++){
   string(out,id(n));string(out,id(n+100));string(out,"record");string(out,"text/plain");string(out,"0".repeat(64));out.writeLong(3);out.writeLong(1);
   string(out,"source");for(int i=0;i<4;i++)out.writeLong(0);out.writeInt(0);out.writeInt(0);out.writeInt(0);out.writeBoolean(false);
   string(out,"");string(out,changedSnapshot&&n==1?"X"+"n".repeat(64999):"n".repeat(65000));for(int i=0;i<4;i++)out.writeInt(0);
  }
  out.writeInt(0);out.writeInt(changedSnapshot?1:0);
  if(changedSnapshot){string(out,id(400));out.writeLong(3);string(out,"OWNER_NOTE");out.writeInt(1);string(out,id(1));string(out,"Owner edited note");}
  out.writeInt(1);string(out,id(300));out.writeLong(2);string(out,"test");string(out,"SUMMARY");string(out,"0".repeat(64));out.writeInt(64);for(int n=1;n<=64;n++)string(out,id(n));string(out,"accepted analysis text");out.writeLong(3);out.writeLong(2);out.writeBoolean(false);out.writeInt(64);
  for(int n=1;n<=64;n++){string(out,id(n+100));string(out,"text/plain");string(out,"0".repeat(64));out.writeLong(3);out.writeBoolean(false);}
  out.writeInt(0);return bytes.toByteArray();
 }
 static Evidence edit(Evidence e,String name,String notes){return new Evidence(e.id(),e.sourceId(),name,e.mime(),e.sha256(),e.size(),e.importedAt(),e.metadata(),e.description(),notes,e.tags(),e.participants(),e.ocr(),e.corrections());}
 static int version(byte[] bytes){return ByteBuffer.wrap(bytes).getInt(4);}
 static void preserved(EvidenceCatalog c){
  check(c.evidence().size()==64);check(c.analyses().size()==1);check(c.analyses().get(0).text().equals("accepted analysis text"));check(c.analyses().get(0).lineage().size()==64);check(c.analyses().get(0).sourceReferences().size()==64);
  for(Evidence e:c.evidence())check(e.notes().equals("n".repeat(65000)));
 }
 public static void main(String[] args)throws Exception{
  for(Owner owner:Owner.values()){
   byte[] v1=legacy(owner,false);check(v1.length==4188265-(owner==Owner.HIDDEN?1:0));check(v1.length<EvidenceCatalog.MAX_BYTES);
   System.out.println("Near-limit "+owner+" v1 bytes="+v1.length+" maximum="+EvidenceCatalog.MAX_BYTES);
   EvidenceCatalog decoded=EvidenceCatalog.decode(v1,owner);preserved(decoded);check(Arrays.equals(v1,decoded.encode()));check(version(decoded.encode())==1);
   reject(()->new EvidenceCatalog(owner,decoded.revision(),decoded.evidence(),decoded.incidents(),decoded.audit(),decoded.analyses(),decoded.rejectedSuggestions()));
   Evidence first=decoded.evidence().get(0);reject(()->decoded.upsertEvidence(edit(first,"renamed",first.notes()),3,"OWNER_RENAME"));check(Arrays.equals(v1,decoded.encode()));
   var reduced=decoded.upsertEvidence(edit(first,first.name(),"Owner explicitly shortened this note"),3,"OWNER_NOTE");reduced.validateSuccessor(decoded);check(version(reduced.encode())==2);check(reduced.encode().length<=EvidenceCatalog.MAX_BYTES);check(reduced.analyses().equals(decoded.analyses()));check(reduced.evidence().subList(1,64).equals(decoded.evidence().subList(1,64)));check(reduced.audit().size()==1);check(EvidenceCatalog.decode(reduced.encode(),owner).analyses().equals(decoded.analyses()));
   byte[] changedLegacy=legacy(owner,true);EvidenceCatalog reconstructedLegacy=EvidenceCatalog.decode(changedLegacy,owner);check(Arrays.equals(changedLegacy,reconstructedLegacy.encode()));check(reconstructedLegacy.audit().size()==1);Arrays.fill(changedLegacy,(byte)0);reject(()->reconstructedLegacy.validateSuccessor(decoded));
   byte[] vaultKey=new byte[32];Arrays.fill(vaultKey,(byte)17);Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(vaultKey,"HmacSHA256"));byte[] derived=mac.doFinal(("private-gallery:evidence:179:v1:"+owner.name()).getBytes(StandardCharsets.UTF_8));
   Path root=Files.createTempDirectory("evidence-v1-migration-");
   try{
    File isolated=root.resolve("evidence-179-"+owner.name().toLowerCase(Locale.ROOT)).toFile();var records=new HiddenRecordStore(isolated);String recordId=owner==Owner.PRIMARY?id(17901):id(17902);
    File stage=records.stage("metadata",recordId,v1,derived);records.promote(stage,"metadata",recordId);records.syncPromotions();byte[] ciphertext=Files.readAllBytes(records.file("metadata",recordId).toPath());
    AtomicInteger commits=new AtomicInteger();var store=new EvidenceEncryptedStore(root.toFile(),owner,()->new EvidenceEncryptedStore.KeyLease(){
     public byte[] key(){return vaultKey.clone();}public boolean current(){return true;}public void commit(EvidenceEncryptedStore.CommitAction action)throws Exception{commits.incrementAndGet();action.run();}public void close(){}
    });
    preserved(store.read());check(commits.get()==0);check(Arrays.equals(ciphertext,Files.readAllBytes(records.file("metadata",recordId).toPath())));
    check(Arrays.equals(v1,store.update(c->c).encode()));check(commits.get()==0);
    reject(()->store.update(c->c.upsertEvidence(edit(c.evidence().get(0),"renamed",c.evidence().get(0).notes()),3,"OWNER_RENAME")));
    check(commits.get()==0);check(Arrays.equals(ciphertext,Files.readAllBytes(records.file("metadata",recordId).toPath())));check(Arrays.equals(v1,records.read("metadata",recordId,derived)));check(isolated.listFiles().length==1);preserved(store.read());
    reject(()->store.update(c->reconstructedLegacy));check(commits.get()==0);check(Arrays.equals(ciphertext,Files.readAllBytes(records.file("metadata",recordId).toPath())));
    var migrated=store.update(c->c.upsertEvidence(edit(c.evidence().get(0),"record","Owner explicitly shortened this note"),4,"OWNER_NOTE"));check(commits.get()==1);byte[] stored=records.read("metadata",recordId,derived);check(version(stored)==2);check(stored.length<=EvidenceCatalog.MAX_BYTES);check(Arrays.equals(stored,migrated.encode()));check(store.read().analyses().equals(decoded.analyses()));check(store.read().evidence().subList(1,64).equals(decoded.evidence().subList(1,64)));check(isolated.listFiles().length==1);
    // A generic callback cannot downgrade an existing v2 state to any reconstructed v1 snapshot.
    reject(()->reconstructedLegacy.validateSuccessor(migrated));Arrays.fill(stored,(byte)0);Arrays.fill(ciphertext,(byte)0);
   }finally{
    Arrays.fill(vaultKey,(byte)0);Arrays.fill(derived,(byte)0);Arrays.fill(v1,(byte)0);
    try(var files=Files.walk(root)){for(Path file:files.sorted(Comparator.reverseOrder()).toList())Files.delete(file);}
   }
  }
  System.out.println("EvidenceMigration179HostTest PASS "+passed);
 }
}
