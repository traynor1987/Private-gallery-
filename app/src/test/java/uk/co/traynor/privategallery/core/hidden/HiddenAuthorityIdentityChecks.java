package uk.co.traynor.privategallery.core.hidden;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Network identity is not a cryptographic-operation capacity reservation. */
public final class HiddenAuthorityIdentityChecks {
 private static void check(boolean value){if(!value)throw new AssertionError();}
 private static void unlock(HiddenSession session){check(session.complete(session.beginAuthentication(),new byte[32]));}
 public static void main(String[] args)throws Exception {
  AtomicLong clock=new AtomicLong(100);
  HiddenSession session=new HiddenSession(clock::get);unlock(session);
  HiddenSession.Authority authority=session.authority();
  ArrayList<HiddenSession.Operation> operations=new ArrayList<>();
  for(int i=0;i<8;i++)operations.add(session.beginOperation(authority));
  check(session.isUnlocked());
  check(session.isAuthorityCurrent(authority));
  ExecutorService threads=Executors.newFixedThreadPool(32);
  try {
   ArrayList<Future<?>> checks=new ArrayList<>();
   for(int n=0;n<64;n++)checks.add(threads.submit(()->{for(int i=0;i<1000;i++)check(session.isAuthorityCurrent(authority));}));
   for(Future<?> future:checks)future.get(10,TimeUnit.SECONDS);
   try{session.beginOperation(authority);throw new AssertionError("Readonly checks changed crypto quota");}catch(IllegalStateException expected){}
   operations.remove(0).close();try(HiddenSession.Operation accepted=session.beginOperation(authority)){check(session.isCurrent(accepted));}
  }finally{threads.shutdownNow();operations.forEach(HiddenSession.Operation::close);}
  session.revokeOperations();check(!session.isAuthorityCurrent(authority));check(session.isUnlocked());
  HiddenSession.Authority current=session.authority();check(session.isAuthorityCurrent(current));
  session.onBackground(30_000);check(!session.isAuthorityCurrent(current));check(session.isUnlocked());
  session.onForeground();current=session.authority();check(session.isAuthorityCurrent(current));
  session.lock();check(!session.isAuthorityCurrent(current));unlock(session);
  check(!session.isAuthorityCurrent(current));check(session.isAuthorityCurrent(session.authority()));
  current=session.authority();clock.decrementAndGet();check(!session.isAuthorityCurrent(current));check(!session.isUnlocked());
  unlock(session);current=session.authority();session.onBackground(30_000);clock.addAndGet(30_000);session.onForeground();check(!session.isAuthorityCurrent(current));check(!session.isUnlocked());
  check(!session.isAuthorityCurrent(null));
  System.out.println("Hidden readonly authority identity: full crypto quota, concurrent probes, revoke/background/expiry/reauth passed");
 }
}
