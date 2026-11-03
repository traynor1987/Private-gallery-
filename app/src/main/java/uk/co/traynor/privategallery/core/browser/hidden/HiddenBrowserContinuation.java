package uk.co.traynor.privategallery.core.browser.hidden;

import java.util.function.LongSupplier;
import uk.co.traynor.privategallery.core.hidden.HiddenSession;

/** Parent-only presentation identity. Never holds an operation or key; no persistent state. */
public final class HiddenBrowserContinuation {
    public enum State { ACTIVE, TEMPORARILY_SUSPENDED, RECONNECTING, LOCKED, TERMINATED }
    private final HiddenSession session;
    private final HiddenSession.Authentication authentication;
    private final LongSupplier clock;
    private final Object child;
    private final int uid,pid;
    private State state=State.ACTIVE;
    private long stoppedAt=-1,timeout,lastObserved;
    private long generation;
    public HiddenBrowserContinuation(HiddenSession session,LongSupplier clock,Object child,int uid,int pid) {
        if(child==null || uid<0 || pid<=0)throw new IllegalArgumentException("Invalid child identity");
        this.session=session;this.authentication=session.authentication();this.clock=clock;
        this.child=child;this.uid=uid;this.pid=pid;lastObserved=clock.getAsLong();
    }
    public synchronized boolean owns(Object endpoint,int uid,int pid) {
        return child==endpoint && this.uid==uid && this.pid==pid && current();
    }
    public synchronized boolean current() {
        if(state==State.LOCKED || state==State.TERMINATED)return false;
        long now=clock.getAsLong();
        if(now<lastObserved || !session.isAuthenticationCurrent(authentication) || (stoppedAt>=0 && now-stoppedAt>=timeout)) {
            synchronized(session) {if(session.isAuthenticationCurrent(authentication))session.lock();}
            state=State.LOCKED;generation++;return false;
        }
        lastObserved=now;return true;
    }
    /** Called only after the transport adapter confirms a denied/dead OS process pin. */
    public synchronized boolean suspend(long at,long grace,boolean deniedPin) {
        if(grace!=0 && grace!=30_000 && grace!=60_000 && grace!=300_000)throw new IllegalArgumentException("Invalid grace");
        long now=clock.getAsLong();
        if(at<0 || at>now)throw new IllegalArgumentException("Invalid foreground loss");
        if(!current())return false;
        if(!deniedPin){state=State.TERMINATED;generation++;return false;}
        if(stoppedAt<0){stoppedAt=at;timeout=grace;generation++;}
        state=State.TEMPORARILY_SUSPENDED;
        return current();
    }
    public synchronized long reconnect() {
        if(!current() || state!=State.TEMPORARILY_SUSPENDED)return -1;
        state=State.RECONNECTING;return ++generation;
    }
    public synchronized boolean activate(long attempt,boolean routeVerified,boolean childBound) {
        if(!current() || state!=State.RECONNECTING || attempt!=generation || !routeVerified || !childBound)return false;
        state=State.ACTIVE;stoppedAt=-1;return true;
    }
    public synchronized State state(){current();return state;}
    public synchronized long stoppedAt(){return stoppedAt;}
    public synchronized void terminate(){state=State.TERMINATED;generation++;}
}
