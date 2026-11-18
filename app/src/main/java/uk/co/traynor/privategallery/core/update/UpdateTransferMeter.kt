package uk.co.traynor.privategallery.core.update

import java.util.Locale

internal fun updateMegabytes(bytes: Long): String = String.format(Locale.UK, "%.1f", bytes.coerceAtLeast(0) / 1_000_000.0)

/** Samples only actual transferred bytes against a monotonic clock; no timer-generated progress. */
internal class UpdateTransferMeter(private val clock: () -> Long = System::nanoTime) {
    data class Sample(val bytesPerSecond: Long?)
    private var previousAt = clock()
    private var previousBytes = 0L
    fun sample(bytes: Long, total: Long): Sample? {
        val now = clock()
        val elapsed = now - previousAt
        if (elapsed < 100_000_000 && bytes != total) return null
        val speed = if (elapsed >= 100_000_000 && bytes >= previousBytes)
            ((bytes - previousBytes).toDouble() * 1_000_000_000 / elapsed).toLong() else null
        previousAt = now
        previousBytes = bytes
        return Sample(speed)
    }
}

internal object UpdateStoragePolicy {
    fun requireSpace(expected: Long, available: Long, maximum: Long) {
        check(expected <= maximum) { "Update exceeds size limit" }
        // Unknown lengths still require room for the maximum bounded transfer.
        val required = if (expected > 0) expected else maximum
        check(available > required + 8L * 1024 * 1024) { "Insufficient storage" }
    }
}
