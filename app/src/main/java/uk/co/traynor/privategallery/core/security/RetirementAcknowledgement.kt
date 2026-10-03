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
    private val terminalRetirement: RetirementAcknowledgement,
    private val accounting: () -> Unit,
    private val onAcknowledged: () -> Unit,
) {
    private val gate = Any()
    private var remaining = children
    private var remainingTerminal = children

    /** Primitive final arrival after this exact child's marker and physical slot return. */
    fun childFinished() {
        val final = synchronized(gate) {
            check(remainingTerminal > 0) { "Duplicate original terminal arrival" }
            remainingTerminal--
            remainingTerminal == 0
        }
        if (final) terminalRetirement.acknowledge()
    }

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
