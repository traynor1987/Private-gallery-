import uk.co.traynor.privategallery.core.evidence.*;
import uk.co.traynor.privategallery.core.hidden.HiddenSession;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.lang.reflect.*;
public final class EvidenceAuthorities179Checks {
 static Class<?> type;
 static int assertions;
 static void check(boolean b,String why){assertions++;if(!b)throw new AssertionError(why);}
 static void refused(Runnable r,String why){try{r.run();throw new AssertionError(why);}catch(IllegalStateException expected){assertions++;}}
 static EvidenceEncryptedStore.LeaseFactory primary(Object identity,Supplier<Object> now,BooleanSupplier current,Supplier<byte[]> key,Object gate)throws Exception{return (EvidenceEncryptedStore.LeaseFactory)type.getMethod("primary",Object.class,Supplier.class,BooleanSupplier.class,Supplier.class,Object.class).invoke(null,identity,now,current,key,gate);}
 static EvidenceEncryptedStore.LeaseFactory hidden(HiddenSession session,HiddenSession.Authority authority,BooleanSupplier current)throws Exception{return (EvidenceEncryptedStore.LeaseFactory)type.getMethod("hidden",HiddenSession.class,HiddenSession.Authority.class,BooleanSupplier.class).invoke(null,session,authority,current);}
 public static void main(String[] args)throws Exception {
  try{type=Class.forName("uk.co.traynor.privategallery.core.evidence.EvidenceAuthorities");}catch(ClassNotFoundException missing){throw new AssertionError("Evidence179 captured lease factories missing",missing);}
  byte[] key=new byte[32];Arrays.fill(key,(byte)73);Object gate=new Object(),id=new Object();var identity=new AtomicReference<Object>(id);var epoch=new AtomicInteger(1);var copies=new AtomicInteger();
  var original=primary(id,identity::get,()->epoch.get()==1,()->{copies.incrementAndGet();return key.clone();},gate);
  var lease=original.acquire();byte[] owned=lease.key();check(owned!=key&&owned[0]==73,"Operation gets an owned key copy");check(lease.current(),"Original authentication current");
  var published=new AtomicBoolean();lease.commit(()->published.set(true));check(published.get(),"Current commit allowed");lease.close();check(Arrays.equals(owned,new byte[32]),"Close wipes key");check(key[0]==73,"Close never wipes session source");refused(lease::key,"Closed lease cannot expose key");
  var old=original.acquire();epoch.set(2);check(!old.current(),"Old epoch rejected even same key identity");published.set(false);try{old.commit(()->published.set(true));throw new AssertionError("Revoked commit allowed");}catch(IllegalStateException expected){check(!published.get(),"Revoked commit never publishes");}old.close();
  int before=copies.get();try{original.acquire();throw new AssertionError("Retired factory revived");}catch(IllegalStateException expected){check(copies.get()==before,"No key copied for retired epoch");}
  var fresh=primary(id,identity::get,()->epoch.get()==2,()->key.clone(),gate);var renewed=fresh.acquire();check(renewed.current(),"Separate current factory allowed");identity.set(new Object());check(!renewed.current(),"Key identity replacement revokes lease");renewed.close();
  HiddenSession session=new HiddenSession(()->100L);byte[] candidate=new byte[32];Arrays.fill(candidate,(byte)31);check(session.complete(session.beginAuthentication(),candidate),"Separate Hidden PIN session authenticates");check(Arrays.equals(candidate,new byte[32]),"Hidden authentication candidate consumed");
  var current=new AtomicBoolean(true);var authority=session.authority();var hiddenFactory=hidden(session,authority,current::get);var hl=hiddenFactory.acquire();var hk=hl.key();check(hk[0]==31,"Hidden lease uses only Hidden key");hl.commit(()->{});session.revokeOperations();check(!hl.current(),"Operation revocation invalidates Hidden lease");check(Arrays.equals(hk,new byte[32]),"Observed Hidden revocation wipes operation key");hl.close();
  byte[] next=new byte[32];Arrays.fill(next,(byte)32);check(session.complete(session.beginAuthentication(),next),"Fresh Hidden auth allowed");try{hiddenFactory.acquire();throw new AssertionError("Stale Hidden authority revived");}catch(IllegalStateException expected){assertions++;}
  var hf=hidden(session,session.authority(),current::get);var active=hf.acquire();var k=active.key();current.set(false);check(!active.current(),"Hidden presentation revocation checked");published.set(false);try{active.commit(()->published.set(true));throw new AssertionError("Hidden presentation commit allowed");}catch(IllegalStateException expected){check(!published.get(),"Hidden presentation revoke blocks publication");}active.close();check(Arrays.equals(k,new byte[32]),"Hidden close wipes copied operation");session.lock();
  System.out.println("Evidence179 captured authority "+assertions+" checks PASS");
 }
}
