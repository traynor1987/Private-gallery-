package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Observe real retirement without installing user callbacks on a funded release worker. */
class RetirementAcknowledgement internal constructor() {
    private val completed = CountDownLatch(1)
    val isComplete: Boolean get() = completed.count == 0L
    /** Only outside application gates; timeout is never a successful acknowledgement. */
    fun await(timeout: Long, unit: TimeUnit): Boolean = completed.await(timeout, unit)
    internal fun acknowledge() = completed.countDown()
}

/** Funded before construction. Arrival has no scan, allocation or nonfinal client callback. */
internal class ReleaseRetirementGroup(
    children: Int,
    private val retirement: RetirementAcknowledgement,
    private val accounting: () -> Unit,
    private val onAcknowledged: () -> Unit,
) {
    private val gate = Any()
    private var remaining = children

    /** Called exactly once per ticket, after every potentially failing child hook returned. */
    fun childReady() {
        val final = synchronized(gate) {
            check(remaining > 0) { "Duplicate original retirement arrival" }
            remaining--
            remaining == 0
        }
        if (final) {
            accounting()
            onAcknowledged()
            retirement.acknowledge()
        }
    }
}
