package uk.co.traynor.privategallery.core.hidden;

import java.util.Arrays;
import java.util.function.LongSupplier;

/** Independent Hidden authority. Owner must lock on background, disposal and Primary lock. */
public final class HiddenSession {
    public static final long TIMEOUT_MILLIS = 60_000;
    public static final long AUTH_TIMEOUT_MILLIS = 30_000;
    private final LongSupplier elapsedRealtime;
    private Attempt pending;
    private byte[] key;
    private long unlockedAt;
    private long lastObserved;

    public static final class Attempt {
        private final long startedAt;
        private Attempt(long startedAt) { this.startedAt = startedAt; }
    }
    public HiddenSession(LongSupplier elapsedRealtime) { this.elapsedRealtime = elapsedRealtime; }

    public synchronized Attempt beginAuthentication() {
        lock();
        pending = new Attempt(elapsedRealtime.getAsLong());
        return pending;
    }
    /** Takes ownership of candidate bytes even when authentication has become stale. */
    public synchronized boolean complete(Attempt attempt, byte[] candidate) {
        try {
            if (attempt == null || attempt != pending) return false;
            pending = null;
            long now = elapsedRealtime.getAsLong();
            if (candidate.length != 32 || now < attempt.startedAt || now - attempt.startedAt >= AUTH_TIMEOUT_MILLIS) return false;
            key = candidate.clone(); unlockedAt = now; lastObserved = now;
            return true;
        } finally { Arrays.fill(candidate, (byte) 0); }
    }
    public synchronized boolean isUnlocked() {
        long now = elapsedRealtime.getAsLong();
        if (key != null && (now < lastObserved || now - unlockedAt >= TIMEOUT_MILLIS)) lock();
        lastObserved = now;
        return key != null;
    }
    public synchronized void lock() {
        pending = null;
        if (key != null) Arrays.fill(key, (byte) 0);
        key = null;
    }
}
