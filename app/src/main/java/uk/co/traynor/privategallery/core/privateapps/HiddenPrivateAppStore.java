package uk.co.traynor.privategallery.core.privateapps;

import uk.co.traynor.privategallery.core.hidden.HiddenSession;
import uk.co.traynor.privategallery.core.hidden.HiddenRecordStore;
import java.io.*;
import java.util.*;
import java.util.function.UnaryOperator;

/** Independent encrypted definitions; no WebView profile or login persistence. */
public final class HiddenPrivateAppStore implements PrivateAppStore {
 private static final Object GATE=new Object();
 private final HiddenSession session;
 private final HiddenSession.Authority authority;
 private final HiddenRecordStore records;
 private final String id;
 private final boolean tor;
 private final java.util.function.BooleanSupplier current;
 private final java.util.function.Consumer<Runnable> publish;
 public HiddenPrivateAppStore(File root,HiddenSession session,HiddenSession.Authority authority,boolean tor) {
  this(root,session,authority,tor,()->true,Runnable::run);
 }
 public HiddenPrivateAppStore(File root,HiddenSession session,HiddenSession.Authority authority,boolean tor,
   java.util.function.BooleanSupplier current,java.util.function.Consumer<Runnable> publish) {
  this.current=Objects.requireNonNull(current);this.publish=Objects.requireNonNull(publish);
  this.session=session;this.authority=Objects.requireNonNull(authority);this.tor=tor;
  records=new HiddenRecordStore(new File(root,"hidden-private-apps-v1"));
  id=tor?"8925faed-2c5f-4975-a05e-c58fbbfe1701":"8925faed-2c5f-4975-a05e-c58fbbfe1702";
 }
 public Owner owner(){return tor?Owner.HIDDEN_TOR:Owner.HIDDEN_VPN;}
 public PrivateAppCatalog read(){return access(null);}
 public PrivateAppCatalog update(UnaryOperator<PrivateAppCatalog> action){return access(Objects.requireNonNull(action));}
 private PrivateAppCatalog access(UnaryOperator<PrivateAppCatalog> action) {
  synchronized(GATE) {
   if(!current.getAsBoolean())throw new IllegalStateException("Private browser revoked");
   try(HiddenSession.Operation op=session.beginOperation(authority)) {
    PrivateAppCatalog catalog=new PrivateAppCatalog(tor,List.of());
    if(records.file("metadata",id).exists()) {
     byte[] bytes=records.read("metadata",id,op.key());
     try{catalog=PrivateAppCatalog.decode(bytes,tor);}finally{Arrays.fill(bytes,(byte)0);}
    }
    if(action!=null) {
     catalog=new PrivateAppCatalog(tor,Objects.requireNonNull(action.apply(catalog)).apps);
     byte[] bytes=catalog.encode();File staged=null;
     try {
      staged=records.stage("metadata",id,bytes,op.key());File ready=staged;
      publish.accept(()->{if(!current.getAsBoolean())throw new IllegalStateException("Private browser revoked");session.commit(op,()->{try{records.promote(ready,"metadata",id);}catch(IOException e){throw new UncheckedIOException(e);}});});
      records.syncPromotions();
     }finally{Arrays.fill(bytes,(byte)0);if(staged!=null)staged.delete();}
    }
    if(!session.isCurrent(op) || !current.getAsBoolean())throw new IllegalStateException("Private app authority revoked");
    return catalog;
   }catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("Private apps unavailable",e);}
  }
 }
}
