package uk.co.traynor.privategallery.core.ui.status;

import java.util.function.Consumer;

/** Event source owned by the visible UI subscription. Implementations emit on the UI thread. */
public interface SystemStatusProvider {
    AutoCloseable subscribe(Consumer<SystemStatusSnapshot> observer);
}
