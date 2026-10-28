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
        for(long timeout:new long[]{0,30_000,60_000,300_000}) {
            clock.set(1_000_000);session.onForeground();session.complete(session.beginAuthentication(),new byte[32]);
            long stopped=clock.get();clock.addAndGet(750);
            session.onBackgroundAt(timeout,stopped);
            if(timeout==0 && session.isUnlocked())throw new AssertionError("Delayed immediate-lock handoff retained Hidden");
            if(timeout>0) {
                clock.set(stopped+timeout-1);if(!session.isUnlocked())throw new AssertionError("Handoff shortened configured grace");
                session.onBackgroundAt(timeout,clock.get()); // duplicate cannot reset the deadline
                clock.incrementAndGet();session.onForeground();
                if(session.isUnlocked())throw new AssertionError("Handoff callback extended configured grace");
            }
        }
        clock.set(2_000_000);session.onForeground();session.complete(session.beginAuthentication(),new byte[32]);
        for(long invalid:new long[]{-1,clock.get()+1}) {
            boolean rejected=false;try{session.onBackgroundAt(60_000,invalid);}catch(IllegalArgumentException expected){rejected=true;}
            if(!rejected || !session.isUnlocked())throw new AssertionError("Invalid handoff timestamp accepted or revoked foreground");
        }
        session.onBackgroundAt(60_000,clock.get()-60_000);
        if(session.isUnlocked())throw new AssertionError("Backdated expired handoff gained fresh grace");
        session.lock();if(session.isUnlocked())throw new AssertionError("Manual lock failed");
        System.out.println("Hidden foreground session has no fixed-minute expiry");
    }
}
