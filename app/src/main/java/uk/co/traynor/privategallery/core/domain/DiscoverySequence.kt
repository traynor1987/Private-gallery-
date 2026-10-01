package uk.co.traynor.privategallery.core.domain

import java.util.UUID

enum class DiscoveryEvent { INSTALLED, VERSION, OTHER }

/** A transient route signal only: no key, credential, storage or persistent state. */
class DiscoveryChallenge internal constructor() {
    val id: UUID = UUID.randomUUID()
}

/** About-owned sequence. Leaving About, backgrounding and exit must call [reset]. */
class DiscoverySequence(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private var progress = 0
    private var startedAt: Long? = null
    private var lastAt: Long? = null

    @Synchronized fun consume(event: DiscoveryEvent): DiscoveryChallenge? {
        val now = clock()
        if (startedAt?.let { now < it || now - it >= DEADLINE_MILLIS } == true ||
            lastAt?.let { now < it } == true) {
            reset()
            return null // The expired event cannot begin a new sequence.
        }
        val expected = if (progress == 5) DiscoveryEvent.VERSION else DiscoveryEvent.INSTALLED
        if (event != expected) {
            reset()
            return null
        }
        if (progress == 0) startedAt = now
        lastAt = now
        progress++
        if (progress != 10) return null
        reset()
        return DiscoveryChallenge()
    }

    @Synchronized fun reset() {
        progress = 0
        startedAt = null
        lastAt = null
    }

    companion object { const val DEADLINE_MILLIS = 30_000L }
}
