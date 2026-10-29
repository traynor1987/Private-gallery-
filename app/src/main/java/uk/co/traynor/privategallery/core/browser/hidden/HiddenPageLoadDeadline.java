package uk.co.traynor.privategallery.core.browser.hidden;

/** Monotonic main-frame deadline; rendering success disarms it without changing transport. */
public final class HiddenPageLoadDeadline {
    private long started=-1;
    public void begin(long now) {started=now;}
    public void visible() {started=-1;}
    public boolean expired(long now) {
        if(started<0 || now-started<60_000)return false;
        started=-1;
        return true;
    }
}
