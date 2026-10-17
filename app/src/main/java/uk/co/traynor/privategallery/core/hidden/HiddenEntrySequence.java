package uk.co.traynor.privategallery.core.hidden;

import java.util.function.LongSupplier;

/** Transient 5 Installed / 1 About version / 4 Installed. Completion grants no authority. */
public final class HiddenEntrySequence {
    private final LongSupplier elapsedRealtime;
    private int step;
    private long startedAt;
    private long lastAt;
    public HiddenEntrySequence(LongSupplier elapsedRealtime) { this.elapsedRealtime = elapsedRealtime; }
    public synchronized boolean tap(boolean version) {
        long now = elapsedRealtime.getAsLong();
        if (step != 0 && (now < lastAt || now - startedAt >= 30_000)) { reset(); return false; }
        if (version != (step == 5)) { reset(); return false; }
        if (step == 0) startedAt = now;
        lastAt = now;
        if (++step != 10) return false;
        reset(); return true;
    }
    public synchronized void reset() { step = 0; startedAt = 0; lastAt = 0; }
}
