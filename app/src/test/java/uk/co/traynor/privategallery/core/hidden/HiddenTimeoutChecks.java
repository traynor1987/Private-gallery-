package uk.co.traynor.privategallery.core.hidden;
import java.util.concurrent.atomic.AtomicLong;

public final class HiddenTimeoutChecks {
    public static void main(String[] args) throws Exception {
        AtomicLong clock=new AtomicLong(100);
        HiddenSession session=new HiddenSession(clock::get);
        session.complete(session.beginAuthentication(),new byte[32]);
        clock.addAndGet(600_001);
        if(!session.isUnlocked())throw new AssertionError("Active foreground Hidden use incorrectly expires after a fixed minute");
        for(long timeout:new long[]{0,30_000,60_000,300_000}) {
            session.onForeground();session.complete(session.beginAuthentication(),new byte[32]);
            HiddenSession.Authority old=session.authority();
            session.onBackground(timeout);
            boolean denied=false;try{session.beginOperation(old);}catch(IllegalStateException expected){denied=true;}
            if(!denied)throw new AssertionError("Background operation admitted");
            if(timeout>0) {
                clock.addAndGet(timeout-1);session.onForeground();
                if(!session.isUnlocked())throw new AssertionError("Grace ended too early");
                denied=false;try{session.beginOperation(old);}catch(IllegalStateException expected){denied=true;}
                if(!denied)throw new AssertionError("Old work inherited foreground authority");
                session.onBackground(timeout);clock.addAndGet(timeout);
            }
            session.onForeground();if(session.isUnlocked())throw new AssertionError("Background deadline not enforced");
        }
        session.complete(session.beginAuthentication(),new byte[32]);
        session.revokeOperations();if(!session.isUnlocked())throw new AssertionError("Tab switching revoked authentication");
        session.onBackground(60_000);clock.addAndGet(30_000);session.onBackground(60_000);
        clock.addAndGet(30_000);session.onForeground();
        if(session.isUnlocked())throw new AssertionError("Duplicate background extended deadline");
        HiddenSession.Attempt oldAttempt=session.beginAuthentication();
        HiddenSession.Attempt freshAttempt=session.beginAuthentication();
        session.rejectAuthentication(oldAttempt);
        if(!session.complete(freshAttempt,new byte[32]))throw new AssertionError("Old authentication failure revoked a new attempt");
        session.lock();if(session.isUnlocked())throw new AssertionError("Manual lock failed");
        System.out.println("Hidden foreground session has no fixed-minute expiry");
    }
}
