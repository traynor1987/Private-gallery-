package uk.co.traynor.privategallery.core.browser.hidden;

/** A failed or retired browser bootstrap must never bind another lease in the same process. */
public final class HiddenVpnProcessAdmission {
    private final java.util.concurrent.atomic.AtomicBoolean entered = new java.util.concurrent.atomic.AtomicBoolean();
    public boolean enter() { return entered.compareAndSet(false, true); }
}
