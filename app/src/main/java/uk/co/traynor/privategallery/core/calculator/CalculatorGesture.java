package uk.co.traynor.privategallery.core.calculator;
import java.util.function.LongSupplier;
/** Input-control events only: never expression characters, history or a credential. */
public final class CalculatorGesture {
    private final LongSupplier clock;
    private int count;
    private long started, last, completedAt;
    private boolean completed;
    public CalculatorGesture(LongSupplier clock) { this.clock = clock; }
    public synchronized boolean hold() {
        long now = clock.getAsLong();
        completed = false;
        if (count > 0 && (now < last || now - started >= 15000)) { reset(); return false; }
        if (count == 0) started = now;
        last = now;
        if (++count < 5) return false;
        reset(); completed = true; completedAt = now; return true;
    }
    public synchronized boolean consume() {
        long now = clock.getAsLong(); boolean allowed = completed && now >= completedAt && now - completedAt < 1000;
        reset(); return allowed;
    }
    public synchronized void reset() { count = 0; started = last = completedAt = 0; completed = false; }
}
