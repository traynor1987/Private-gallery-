package uk.co.traynor.privategallery.core.evidence;

import uk.co.traynor.privategallery.core.hidden.HiddenRecordStore;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Separate owner/domain catalogue, sharing only the existing authenticated metadata record cipher. */
public final class EvidenceEncryptedStore {
 public interface LeaseFactory {KeyLease acquire()throws Exception;}
 public interface KeyLease extends AutoCloseable {
  byte[] key();
  boolean current();
  void commit(CommitAction action)throws Exception;
  @Override void close();
 }
 public interface CommitAction {void run()throws Exception;}
 private static final ConcurrentHashMap<String,Object> LOCKS=new ConcurrentHashMap<>();
 private final EvidenceCatalog.Owner owner;
 private final LeaseFactory leases;
 private final HiddenRecordStore records;
 private final String recordId;
 private final Object lock;
 public EvidenceEncryptedStore(File root,EvidenceCatalog.Owner owner,LeaseFactory leases) {
  this.owner=Objects.requireNonNull(owner);this.leases=Objects.requireNonNull(leases);
  try {File isolated=new File(Objects.requireNonNull(root),"evidence-179-"+owner.name().toLowerCase(Locale.ROOT)).getCanonicalFile();records=new HiddenRecordStore(isolated);lock=LOCKS.computeIfAbsent(isolated.getPath()+":"+owner.name(),key->new Object());}
  catch(java.io.IOException e){throw new IllegalArgumentException("Evidence storage unavailable",e);}
  recordId=owner==EvidenceCatalog.Owner.PRIMARY?"00000000-0000-4000-8000-000000017901":"00000000-0000-4000-8000-000000017902";
 }
 public EvidenceCatalog read(){return access(null);}
 public EvidenceCatalog update(UnaryOperator<EvidenceCatalog> action){return access(Objects.requireNonNull(action));}
 private EvidenceCatalog access(UnaryOperator<EvidenceCatalog> action) {
  synchronized(lock) {
   byte[] vaultKey=null,key=null,decoded=null,encoded=null;File stage=null;
   try(KeyLease lease=Objects.requireNonNull(leases.acquire())) {
    requireCurrent(lease);vaultKey=Objects.requireNonNull(lease.key());if(vaultKey.length!=32)throw new IllegalArgumentException("Invalid evidence key");
    Mac hmac=Mac.getInstance("HmacSHA256");hmac.init(new SecretKeySpec(vaultKey,"HmacSHA256"));key=hmac.doFinal(("private-gallery:evidence:179:v1:"+owner.name()).getBytes(StandardCharsets.UTF_8));requireCurrent(lease);
    records.clearStaging();EvidenceCatalog catalog=EvidenceCatalog.empty(owner);
    if(records.file("metadata",recordId).exists()){decoded=records.read("metadata",recordId,key);catalog=EvidenceCatalog.decode(decoded,owner);}
    requireCurrent(lease);
    if(action!=null){EvidenceCatalog updated=Objects.requireNonNull(action.apply(catalog));if(updated.owner()!=owner)throw new IllegalArgumentException("Foreign evidence owner");requireCurrent(lease);
     if(updated==catalog)return catalog;
     // Prevent generic callback rollback/replacement from bypassing audited domain methods.
     updated.validateSuccessor(catalog);
     encoded=updated.encode();stage=records.stage("metadata",recordId,encoded,key);requireCurrent(lease);File prepared=stage;
     lease.commit(()->{requireCurrent(lease);records.promote(prepared,"metadata",recordId);});
     // Expensive durability IO belongs outside the authentication monitor. Failure here means
     // uncertain durability after promotion: propagate; caller must reread before retry.
     records.syncPromotions();requireCurrent(lease);catalog=updated;
    }
    requireCurrent(lease);return catalog;
   }catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("Evidence storage operation failed; reread before retry",e);}
   finally {wipe(vaultKey);wipe(key);wipe(decoded);wipe(encoded);if(stage!=null)stage.delete();}
  }
 }
 private static void requireCurrent(KeyLease lease){if(!lease.current())throw new IllegalStateException("Evidence authority revoked");}
 private static void wipe(byte[] bytes){if(bytes!=null)Arrays.fill(bytes,(byte)0);}
}
