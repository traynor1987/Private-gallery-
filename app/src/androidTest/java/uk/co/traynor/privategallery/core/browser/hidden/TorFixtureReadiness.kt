package uk.co.traynor.privategallery.core.browser.hidden

import android.net.Network
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/** Take each sample after earlier service callbacks on the main looper have returned.
 * Only absence of the owned Network is retryable. Revocation or an invalid configuration fails.
 */
internal fun <T : Any> awaitTorFixtureSnapshot(readOnMain: () -> T?): T {
    check(Looper.myLooper() !== Looper.getMainLooper())
    val main = Handler(Looper.getMainLooper())
    val deadline = SystemClock.elapsedRealtime() + 30_000
    while (true) {
        val remaining = deadline - SystemClock.elapsedRealtime()
        check(remaining > 0) { "Timed out waiting for the owned Tor isolation network" }
        val sample = FutureTask<T?> { readOnMain() }
        check(main.post(sample))
        sample.get(remaining, TimeUnit.MILLISECONDS)?.let { return it }
        SystemClock.sleep(50)
    }
}

internal fun HiddenTorIsolationConnection.awaitFixtureRoute(authorized: () -> Boolean):
    Pair<Network, HiddenTorConfiguration> = awaitTorFixtureSnapshot {
    check(authorized()) { "Tor isolation authority retired during fixture startup" }
    network()?.let { it to configuration(Binder()) }
}
