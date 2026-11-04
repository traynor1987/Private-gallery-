package uk.co.traynor.privategallery.core.browser.hidden;

import java.util.Objects;
import java.util.function.LongSupplier;
import uk.co.traynor.privategallery.core.hidden.HiddenSession;

/**
 * Parent-only presentation identity. Never holds an operation or key; no persistent state.
 * All mutable state uses the owning HiddenSession monitor. There is deliberately
 * no Continuation monitor: final authenticated publication may call these methods
 * while already holding Session, without reversing Continuation -> Session order.
 * The trusted monotonic clock must be bounded/nonblocking. No I/O, broker locks,
 * native retirement, or transport callbacks may be introduced in these sections.
 */
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
        this.session=Objects.requireNonNull(session);this.clock=Objects.requireNonNull(clock);
        this.child=child;this.uid=uid;this.pid=pid;
        synchronized(session) {this.authentication=session.authentication();lastObserved=clock.getAsLong();}
    }
    public boolean owns(Object endpoint,int uid,int pid) {
        synchronized(session) {return child==endpoint && this.uid==uid && this.pid==pid && currentLocked();}
    }
    public boolean current() {synchronized(session) {return currentLocked();}}
    private boolean currentLocked() {
        if(state==State.LOCKED || state==State.TERMINATED)return false;
        long now=clock.getAsLong();
        if(now<lastObserved || !session.isAuthenticationCurrent(authentication) || (stoppedAt>=0 && now-stoppedAt>=timeout)) {
            if(session.isAuthenticationCurrent(authentication))session.lock();
            state=State.LOCKED;generation++;return false;
        }
        lastObserved=now;return true;
    }
    /** Called only after the transport adapter confirms a denied/dead OS process pin.
     * This existing receipt is NOT proof that the unfinished shipping VPN fence is fixed. */
    public boolean suspend(long at,long grace,boolean deniedPin) {
        synchronized(session) {
            if(grace!=0 && grace!=30_000 && grace!=60_000 && grace!=300_000)throw new IllegalArgumentException("Invalid grace");
            long now=clock.getAsLong();
            if(at<0 || at>now)throw new IllegalArgumentException("Invalid foreground loss");
            if(!currentLocked())return false;
            if(!deniedPin){state=State.TERMINATED;generation++;return false;}
            if(stoppedAt<0){stoppedAt=at;timeout=grace;generation++;}
            state=State.TEMPORARILY_SUSPENDED;
            return currentLocked();
        }
    }
    public long reconnect() {
        synchronized(session) {
            if(!currentLocked() || state!=State.TEMPORARILY_SUSPENDED)return -1;
            state=State.RECONNECTING;return ++generation;
        }
    }
    public boolean activate(long attempt,boolean routeVerified,boolean childBound) {
        synchronized(session) {
            if(!currentLocked() || state!=State.RECONNECTING || attempt!=generation || !routeVerified || !childBound)return false;
            state=State.ACTIVE;stoppedAt=-1;return true;
        }
    }
    public State state(){synchronized(session) {currentLocked();return state;}}
    public long stoppedAt(){synchronized(session) {return stoppedAt;}}
    public void terminate(){synchronized(session) {state=State.TERMINATED;generation++;}}
}
