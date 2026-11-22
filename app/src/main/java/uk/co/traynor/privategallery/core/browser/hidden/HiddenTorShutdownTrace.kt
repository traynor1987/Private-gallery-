package uk.co.traynor.privategallery.core.browser.hidden

import android.os.SystemClock
import android.util.Log
import uk.co.traynor.privategallery.BuildConfig

/** Debug instrumentation records only lifecycle labels, timestamps and byte counts. */
internal object HiddenTorShutdownTrace {
    fun event(name: String, detail: String = "") {
        if (BuildConfig.DEBUG) {
            Log.i("NovaTorShutdown", "event=$name elapsed_ns=${SystemClock.elapsedRealtimeNanos()} $detail")
        }
    }
}
