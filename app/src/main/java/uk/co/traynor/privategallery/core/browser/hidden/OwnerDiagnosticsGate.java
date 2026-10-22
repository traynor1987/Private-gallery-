package uk.co.traynor.privategallery.core.browser.hidden;

import java.util.function.LongSupplier;

/** Discovery only; never grants authentication. No persisted state. */
public final class OwnerDiagnosticsGate {
    private final LongSupplier clock;
    private int taps;
    private long started, last;
    public OwnerDiagnosticsGate(LongSupplier clock) { this.clock=clock; }
    public boolean tap() {
        long now=clock.getAsLong();
        if(taps>0 && (now<last || now-started>=30_000)) { reset(); return false; }
        if(taps==0) started=now;
        last=now;
        if(++taps<7) return false;
        reset(); return true;
    }
    public void reset() { taps=0; started=0; last=0; }
}
