package uk.co.traynor.privategallery.core.evidence;

import uk.co.traynor.privategallery.core.hidden.HiddenSession;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Captured authentication leases. The caller's guard includes an immutable auth epoch, never just unlocked. */
public final class EvidenceAuthorities {
 private EvidenceAuthorities() {}
 private static boolean allowed(BooleanSupplier current){try{return current.getAsBoolean();}catch(RuntimeException denied){return false;}}
 private static void require(boolean condition){if(!condition)throw new IllegalStateException("Evidence authority revoked");}
 public static EvidenceEncryptedStore.LeaseFactory primary(Object capturedIdentity,Supplier<Object> currentIdentity,
   BooleanSupplier authorized,Supplier<byte[]> copyKey,Object commitGate) {
  Objects.requireNonNull(capturedIdentity);Objects.requireNonNull(currentIdentity);Objects.requireNonNull(authorized);Objects.requireNonNull(copyKey);Objects.requireNonNull(commitGate);
  return ()->{synchronized(commitGate) {
   require(currentIdentity.get()==capturedIdentity&&allowed(authorized));
   byte[] owned=Objects.requireNonNull(copyKey.get());
   if(owned.length!=32){Arrays.fill(owned,(byte)0);throw new IllegalStateException("Invalid evidence key");}
   return new EvidenceEncryptedStore.KeyLease() {
    private boolean closed;
    private boolean valid(){if(closed)return false;if(currentIdentity.get()!=capturedIdentity||!allowed(authorized)){closed=true;Arrays.fill(owned,(byte)0);return false;}return true;}
    @Override public byte[] key(){synchronized(commitGate){require(valid());return owned;}}
    @Override public boolean current(){synchronized(commitGate){return valid();}}
    @Override public void commit(EvidenceEncryptedStore.CommitAction action)throws Exception {synchronized(commitGate){require(valid());action.run();require(valid());}}
    @Override public void close(){synchronized(commitGate){closed=true;Arrays.fill(owned,(byte)0);}}
   };
  }};
 }
 /** authorized must be a nonblocking captured flag/session check, never acquire a reverse-order owner lock. */
 public static EvidenceEncryptedStore.LeaseFactory hidden(HiddenSession session,HiddenSession.Authority captured,
   BooleanSupplier authorized) {
  Objects.requireNonNull(session);Objects.requireNonNull(captured);Objects.requireNonNull(authorized);
  return ()->{
   require(allowed(authorized));HiddenSession.Operation operation=session.beginOperation(captured);
   AtomicBoolean closed=new AtomicBoolean();
   EvidenceEncryptedStore.KeyLease lease=new EvidenceEncryptedStore.KeyLease() {
    @Override public boolean current(){if(closed.get())return false;if(!allowed(authorized)||!session.isCurrent(operation)){close();return false;}return true;}
    @Override public byte[] key(){require(current());return operation.key();}
    @Override public void commit(EvidenceEncryptedStore.CommitAction action) {
     require(current());session.commit(operation,()->{require(current());try{action.run();}catch(RuntimeException|Error failure){throw failure;}catch(Exception failure){throw new IllegalStateException("Evidence commit failed",failure);}require(current());});
    }
    @Override public void close(){if(closed.compareAndSet(false,true))operation.close();}
   };
   if(!lease.current()){lease.close();throw new IllegalStateException("Evidence authority revoked");}
   return lease;
  };
 }
}
