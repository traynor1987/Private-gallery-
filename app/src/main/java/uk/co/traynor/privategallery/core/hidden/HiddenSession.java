package uk.co.traynor.privategallery.core.hidden;

import java.util.Arrays;
import java.util.function.LongSupplier;

/** Independent Hidden authority. Background grace is independent; explicit lock/owner disposal always revokes. */
public final class HiddenSession {
    public static final long TIMEOUT_MILLIS = 60_000;
    public static final long AUTH_TIMEOUT_MILLIS = 30_000;
    private final LongSupplier elapsedRealtime;
    private Attempt pending;
    private final java.util.Set<Operation> operations = new java.util.HashSet<>();
    private long generation;
    private byte[] key;
    private boolean backgrounded;
    private long backgroundedAt = -1;
    private long backgroundTimeout;
    private long lastObserved;

    public static final class Attempt {
        private final long startedAt;
        private Attempt(long startedAt) { this.startedAt = startedAt; }
    }
    public HiddenSession(LongSupplier elapsedRealtime) { this.elapsedRealtime = elapsedRealtime; }

    public synchronized Attempt beginAuthentication() {
        if (backgrounded) throw new IllegalStateException("Hidden authentication requires foreground");
        lock();
        pending = new Attempt(elapsedRealtime.getAsLong());
        return pending;
    }
    /** Takes ownership of candidate bytes even when authentication has become stale. */
    public synchronized boolean complete(Attempt attempt, byte[] candidate) {
        try {
            if (backgrounded || attempt == null || attempt != pending) return false;
            pending = null;
            long now = elapsedRealtime.getAsLong();
            if (candidate.length != 32 || now < attempt.startedAt || now - attempt.startedAt >= AUTH_TIMEOUT_MILLIS) return false;
            key = candidate.clone(); lastObserved = now;
            return true;
        } finally { Arrays.fill(candidate, (byte) 0); }
    }
    /** A failed old IO result must not revoke a newer authentication. */
    public synchronized void rejectAuthentication(Attempt attempt) {if (attempt != null && attempt == pending) lock();}
    public synchronized boolean isUnlocked() {
        long now = elapsedRealtime.getAsLong();
        if (key != null && (now < lastObserved || (backgroundedAt >= 0 && now - backgroundedAt >= backgroundTimeout))) lock();
        lastObserved = now;
        return key != null;
    }
    /** Small bounded operation scope. Never serves as Primary authority. */
    public final class Operation implements AutoCloseable {
        private final long epoch;
        private final byte[] bytes;
        private boolean closed;
        private boolean revoked;
        private Operation() { epoch = generation; bytes = key.clone(); }
        public byte[] key() { return bytes; }
        @Override public void close() {
            synchronized (HiddenSession.this) {
                Arrays.fill(bytes, (byte) 0); closed = true; operations.remove(this);
            }
        }
    }
    /** Opaque epoch binding: queued work must never inherit a later authentication. */
    public static final class Authority {
        private final HiddenSession owner;
        private final long epoch;
        private Authority(HiddenSession owner,long epoch) {this.owner=owner;this.epoch=epoch;}
    }
    public synchronized Authority authority() {
        if (backgrounded || !isUnlocked()) throw new IllegalStateException("Hidden locked or backgrounded");
        return new Authority(this,generation);
    }
    public synchronized Operation beginOperation() { return beginOperation(authority()); }
    public synchronized Operation beginOperation(Authority authority) {
        if (authority == null || authority.owner != this || authority.epoch != generation || backgrounded || !isUnlocked() || operations.size() >= 8) throw new IllegalStateException("Hidden locked or busy");
        Operation operation = new Operation(); operations.add(operation); return operation;
    }
    public synchronized boolean isCurrent(Operation operation) {
        return !backgrounded && isUnlocked() && operation != null && !operation.closed && !operation.revoked && operation.epoch == generation && operations.contains(operation);
    }
    /** Only the short final promotion belongs here; expensive encryption stays outside this monitor. */
    public synchronized void commit(Operation operation, Runnable promotion) {
        if (!isCurrent(operation)) throw new IllegalStateException("Hidden session revoked");
        promotion.run();
    }
    /** Cancel old presentation/work without discarding the independently authenticated key. */
    public synchronized void revokeOperations() {
        generation++;
        for (Operation operation : operations) operation.revoked = true;
        operations.clear();
        pending = null;
    }
    public synchronized void onBackground(long timeoutMillis) {
        if (timeoutMillis != 0 && timeoutMillis != 30_000 && timeoutMillis != 60_000 && timeoutMillis != 300_000) throw new IllegalArgumentException("Unsupported Hidden timeout");
        if (backgrounded) {isUnlocked();return;} // Duplicate lifecycle delivery must not extend grace.
        long now = elapsedRealtime.getAsLong();
        if (now < lastObserved) lock();
        revokeOperations();
        backgrounded = true; backgroundedAt = now; backgroundTimeout = timeoutMillis;
        if (timeoutMillis == 0) lock();
    }
    public synchronized void onForeground() {
        isUnlocked(); // Check the old deadline before clearing it.
        backgrounded = false; backgroundedAt = -1;
    }
    public synchronized void lock() {
        revokeOperations();
        if (key != null) Arrays.fill(key, (byte) 0);
        key = null; backgroundedAt = -1;
    }
}
