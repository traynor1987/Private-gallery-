package uk.co.traynor.privategallery.core.ui.status;

import java.util.function.Consumer;

/** Owns subscription retirement, including queued emissions from an earlier visibility generation. */
public final class SystemStatusSession implements AutoCloseable {
    private final SystemStatusProvider provider;
    private final Consumer<SystemStatusSnapshot> observer;
    private AutoCloseable subscription;
    private long generation;
    private boolean active;
    private boolean closed;

    public SystemStatusSession(SystemStatusProvider provider, Consumer<SystemStatusSnapshot> observer) {
        this.provider = provider;
        this.observer = observer;
    }

    public synchronized void setVisible(boolean enabled, boolean resumed, boolean locked, boolean statusBarVisible) {
        boolean wanted = enabled && resumed && !locked && !statusBarVisible && !closed;
        if (wanted == active) return;
        active = wanted;
        long token = ++generation;
        retireSubscription();
        if (wanted) {
            AutoCloseable opened = provider.subscribe(value -> {
                synchronized (SystemStatusSession.this) {
                    if (!closed && active && generation == token) observer.accept(value);
                }
            });
            // A synchronous initial emission is allowed to close/retire this session.
            if (active && !closed && generation == token) subscription = opened;
            else closeQuietly(opened);
        }
    }

    @Override public synchronized void close() {
        closed = true;
        active = false;
        generation++;
        retireSubscription();
    }

    private void retireSubscription() {
        AutoCloseable old = subscription;
        subscription = null;
        closeQuietly(old);
    }
    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try { closeable.close(); } catch (Exception ignored) { /* Already retired. */ }
        }
    }
}
