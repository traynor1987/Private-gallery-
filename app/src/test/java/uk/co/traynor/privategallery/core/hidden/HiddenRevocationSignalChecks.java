package uk.co.traynor.privategallery.core.hidden;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Host proof of synchronous authority revocation, epoch fencing, and nonblocking signal pattern. */
public final class HiddenRevocationSignalChecks {
    private static HiddenSession unlocked(AtomicLong clock) {
        HiddenSession value=new HiddenSession(clock::get);
        if(!value.complete(value.beginAuthentication(),new byte[32]))throw new AssertionError("setup");
        return value;
    }
    public static void main(String[] args) throws Exception {
        AtomicLong clock=new AtomicLong(100);
        HiddenSession session=unlocked(clock);
        HiddenSession.Authority old=session.authority();AtomicInteger hits=new AtomicInteger();
        session.onRevoked(old,hits::incrementAndGet);
        session.lock();if(hits.get()!=1)throw new AssertionError("Signal was not delivered before lock returned");
        session.lock();if(hits.get()!=1)throw new AssertionError("Old signal repeated");
        session.complete(session.beginAuthentication(),new byte[32]);
        session.onRevoked(old,hits::incrementAndGet).close();
        if(hits.get()!=2 || !session.isUnlocked())throw new AssertionError("Stale registration was not signaled immediately / inherited new auth");
        HiddenSession.RevocationRegistration removed=session.onRevoked(session.authority(),hits::incrementAndGet);
        removed.close();session.revokeOperations();if(hits.get()!=2)throw new AssertionError("Detached signal fired");
        session.onRevoked(session.authority(),hits::incrementAndGet);
        session.onBackground(300_000);
        if(hits.get()!=3 || !session.isUnlocked())throw new AssertionError("Background must revoke transport while preserving independent grace");
        session.onForeground();
        session.onRevoked(session.authority(),hits::incrementAndGet);
        session.onRevoked(session.authority(),()->{throw new AssertionError("Synthetic callback failure");});
        session.lock();if(hits.get()!=4 || session.isUnlocked())throw new AssertionError("Callback failure interrupted key/other-signal revocation");
        ExecutorService pool=Executors.newFixedThreadPool(3,r->{Thread t=new Thread(r,"revocation-test");t.setDaemon(true);return t;});
        try {
            for(int i=0;i<200;i++) {
                HiddenSession current=unlocked(clock);Object brokerGate=new Object();
                CountDownLatch holding=new CountDownLatch(1),signal=new CountDownLatch(1),cleaned=new CountDownLatch(1);
                current.onRevoked(current.authority(),()->{
                    signal.countDown(); // Same restricted pattern as broker: atomic/signal + queued cleanup.
                    pool.execute(()->{synchronized(brokerGate){current.isUnlocked();cleaned.countDown();}});
                });
                Future<?> gateHolder=pool.submit(()->{synchronized(brokerGate){
                    holding.countDown();try{if(!signal.await(2,TimeUnit.SECONDS))throw new AssertionError("signal blocked on broker gate");}catch(InterruptedException e){throw new AssertionError(e);}
                    current.isUnlocked();
                }});
                Future<?> revoke=pool.submit(()->{try{holding.await();}catch(InterruptedException e){throw new AssertionError(e);}current.lock();});
                revoke.get(3,TimeUnit.SECONDS);gateHolder.get(3,TimeUnit.SECONDS);
                if(!cleaned.await(3,TimeUnit.SECONDS))throw new AssertionError("cleanup lock inversion");
            }
            for(int i=0;i<200;i++) {
                HiddenSession current=unlocked(clock);HiddenSession.Authority authority=current.authority();AtomicInteger calls=new AtomicInteger();CountDownLatch go=new CountDownLatch(1);
                Future<?> register=pool.submit(()->{try{go.await();}catch(InterruptedException e){throw new AssertionError(e);}current.onRevoked(authority,calls::incrementAndGet);});
                Future<?> revoke=pool.submit(()->{try{go.await();}catch(InterruptedException e){throw new AssertionError(e);}current.lock();});
                go.countDown();register.get(3,TimeUnit.SECONDS);revoke.get(3,TimeUnit.SECONDS);
                if(calls.get()!=1)throw new AssertionError("Register/revoke race lost or duplicated signal");
            }
        } finally {pool.shutdownNow();}
        System.out.println("Hidden synchronous revocation signal: return boundary, epoch/race/failure and lock-inversion stress passed");
    }
}
