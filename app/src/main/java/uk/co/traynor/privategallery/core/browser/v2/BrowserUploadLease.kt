package uk.co.traynor.privategallery.core.browser.v2

import kotlinx.coroutines.Job
import uk.co.traynor.privategallery.core.security.OwnedResource

/** One published upload keeps both original file retirement and its expiry producer. */
internal class BrowserUploadLease<T : AutoCloseable>(
    val copies: OwnedResource<T>,
    private val timer: Job,
) : AutoCloseable {
    fun startCleanupTimer(): Boolean = timer.start()
    override fun close() {
        try { copies.close() } finally { timer.cancel() }
    }
}
