package uk.co.traynor.privategallery.core.hidden;

import java.util.function.LongSupplier;

/** Transient 5 Installed / 1 About version / 4 Installed. Completion grants no authority. */
public final class HiddenEntrySequence {
    private final LongSupplier elapsedRealtime;
    private int step;
    private long startedAt;
    private long lastAt;
    private boolean completed;
    private long completedAt;
    public HiddenEntrySequence(LongSupplier elapsedRealtime) { this.elapsedRealtime = elapsedRealtime; }
    public synchronized boolean tap(boolean version) {
        long now = elapsedRealtime.getAsLong();
        completed = false;
        if (step != 0 && (now < lastAt || now - startedAt >= 30_000)) { reset(); return false; }
        if (version != (step == 5)) { reset(); return false; }
        if (step == 0) startedAt = now;
        lastAt = now;
        if (++step != 10) return false;
        reset(); completed = true; completedAt = now; return true;
    }
    /** A transient one-use admission, never a credential or persisted discovery flag. */
    public synchronized boolean consumeCompletion() {
        long now = elapsedRealtime.getAsLong();
        boolean allowed = completed && now >= completedAt && now - completedAt < 30_000;
        completed = false; completedAt = 0;
        return allowed;
    }
    public synchronized void reset() { step = 0; startedAt = 0; lastAt = 0; completed = false; completedAt = 0; }
}
