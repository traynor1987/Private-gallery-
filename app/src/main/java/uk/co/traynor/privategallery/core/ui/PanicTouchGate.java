package uk.co.traynor.privategallery.core.ui;

/** Android action constants, with no Android dependency. 0=pass, 1=trigger, 2=consume. */
public final class PanicTouchGate {
    private boolean consumed;
    public int dispatch(int action, int pointers, boolean enabled, boolean exploring) {
        if (action == 0) consumed = false;
        if (consumed) {
            if (action == 1 || action == 3) consumed = false;
            return 2;
        }
        if (enabled && !exploring && action == 5 && pointers >= 3) {
            consumed = true;
            return 1;
        }
        return 0;
    }
}
