package uk.co.traynor.privategallery.core.security

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch

/**
 * Release capacity only; a ticket grants no key, operation, path, IO or promotion authority.
 * Every independently blocking child needs its own ticket BEFORE construction. Integration
 * must check the original operation and reserve its complete child manifest atomically.
 */
internal object ProcessReleaseCapacity {
    val primaryIo = ReleasePool(16)
    val primaryPresentation = ReleasePool(16)
    val hiddenIo = ReleasePool(16)
    val hiddenPresentation = ReleasePool(16)
    val proof = ReleasePool(8)
}

/** Fixed workers cannot be borrowed by another pool or multiplied by newer authority epochs. */
internal class ReleasePool(
    capacity: Int,
    private val allocateInvocation: (() -> Unit, () -> Unit) -> ReleaseInvocation = { action, returned -> ReleaseInvocation(action, returned) },
    private val allocateTicket: (Any, ReleasePool.Slot) -> ReleaseTicket = { owner, slot -> ReleaseTicket(owner, slot) },
) {
    private val gate = Any()
    private val slots = Array(capacity) { Slot(it) }
    private var admissionFailed = false
    private var retirementDispatches = 0
    init { require(capacity > 0) }
    val occupied: Int get() = synchronized(gate) { slots.count { it.ticket != null } }
    /** A new authority cannot reset an older authority's actual retirement obligation. */
    val hasUnacknowledgedRetirement: Boolean get() = synchronized(gate) {
        admissionFailed || retirementDispatches != 0 || slots.any { it.ticket?.hasUnacknowledgedRetirement == true }
    }
    /** A failed registry snapshot cannot make undispatched revoked children disappear. */
    internal fun failRetirementAdmission() = synchronized(gate) { admissionFailed = true }
    internal fun beginRetirementDispatch() = synchronized(gate) {
        if (retirementDispatches == Int.MAX_VALUE) {
            admissionFailed = true
            error("Retirement dispatch capacity unavailable")
        }
        retirementDispatches++
    }
    internal fun endRetirementDispatch() = synchronized(gate) {
        check(retirementDispatches > 0)
        retirementDispatches--
    }

    fun reserve(owner: Any): ReleaseTicket = reserveAll(owner, 1).single()

    /** All child slots are selected and physically funded before any ticket becomes visible. */
    fun reserveAll(owner: Any, count: Int): List<ReleaseTicket> = synchronized(gate) {
        check(!admissionFailed) { "Original retirement registry unavailable" }
        require(count > 0 && count <= slots.size) { "Invalid release manifest" }
        val selected = slots.filter { it.ticket == null && it.available }.take(count)
        check(selected.size == count) { "Release capacity unavailable" }
        // Startup can fail, but no partially allocated manifest escapes and no factory has run.
        selected.forEach { it.start() }
        // Complete every potentially allocating step before publishing ANY occupied slot.
        // If allocation fails, funded workers remain idle and reusable; no factory has run.
        val result = selected.map { slot -> allocateTicket(owner, slot) }
        check(selected.all { it.available }) { "Original release worker unavailable" }
        var index = 0
        while (index < selected.size) {
            selected[index].ticket = result[index]
            index++
        }
        result
    }

    internal inner class Slot(private val index: Int) {
        val pool: ReleasePool get() = this@ReleasePool
        var ticket: ReleaseTicket? = null // guarded by the pool gate
        private val requests = ArrayBlockingQueue<ReleaseInvocation>(1)
        private var started = false
        private var startupFailed = false
        @Volatile private var workerDead = false
        val available: Boolean get() = !startupFailed && !workerDead

        fun start() {
            check(available) { "Release worker unavailable" }
            if (started) return
            val ready = CountDownLatch(1)
            try {
                Thread({
                    ready.countDown()
                    try {
                        while (true) {
                            // A client release may set the interrupt flag. It cannot revoke this
                            // process-owned worker's independently reserved execution capacity.
                            Thread.interrupted()
                            val next = try { requests.take() } catch (_: InterruptedException) { continue }
                            try { next.action() } finally { next.returned() }
                        }
                    } finally {
                        workerDead = true
                        val outstanding = synchronized(gate) { ticket }
                        outstanding?.workerFailed()
                    }
                }, "owned-release-$index").apply { isDaemon = true }.start()
                ready.await()
                started = true
            } catch (failure: Throwable) {
                startupFailed = true
                if (failure is InterruptedException) Thread.currentThread().interrupt()
                throw IllegalStateException("Release worker unavailable", failure)
            }
        }

        fun prepare(action: () -> Unit, returned: () -> Unit): ReleaseInvocation =
            allocateInvocation(action, returned)

        fun submit(invocation: ReleaseInvocation) {
            check(requests.offer(invocation)) { "Duplicate release dispatch" }
        }

        fun recycle(original: ReleaseTicket) = synchronized(gate) {
            check(ticket === original) { "Release ticket identity changed" }
            ticket = null
        }
    }

}

internal data class ReleaseInvocation(val action: () -> Unit, val returned: () -> Unit)

/** One actual independent release action, including partial construction and normal retirement. */
internal class ReleaseTicket internal constructor(
    private val owner: Any,
    private val slot: ReleasePool.Slot,
) {
    internal val retirementPool: ReleasePool get() = slot.pool
    private val gate = Any()
    private var constructing = false
    private var constructionFinished = false
    private var released = false
    private var resource: AutoCloseable? = null
    private var dispatched = false
    private var invocationReturned = false
    private var acknowledged = false
    private var failure: Throwable? = null
    private var terminal = false
    private var retirement: (() -> Unit)? = null
    private var retirementNotified = false
    private var retirementAccounting: (() -> Unit)? = null
    private var retirementAccountingReturned = true
    private var owningRetirement: (() -> Unit)? = null
    private var owningRetirementReturned = true
    private var owningPublication: (() -> Unit)? = null
    private var owningPublicationReturned = true
    private var retirementGroup: ReleaseRetirementGroup? = null
    private var groupReturned = true

    // These records and callbacks are funded before reserveAll publishes this ticket.
    private val acknowledgement = java.util.function.BiConsumer<Unit?, Throwable?> { _, problem ->
        synchronized(gate) {
            if (problem != null) failure = problem else acknowledged = true
        }
        finishIfReady()
    }
    private val invocation = slot.prepare(::invokeRelease, ::releaseReturned)

    val finished: Boolean get() = synchronized(gate) { terminal || failure != null }
    val failed: Boolean get() = synchronized(gate) { failure != null }
    val successful: Boolean get() = synchronized(gate) {
        terminal && failure == null && retirementAccountingReturned && owningRetirementReturned && owningPublicationReturned && groupReturned
    }
    internal val hasUnacknowledgedRetirement: Boolean get() = synchronized(gate) {
        failure != null || released && !(terminal && retirementAccountingReturned && owningRetirementReturned && owningPublicationReturned && groupReturned)
    }
    fun belongsTo(originalOwner: Any): Boolean = owner === originalOwner

    /** Owning-registry accounting is installed before release and funded by this slot. */
    internal fun onRetirementAccounting(accounting: () -> Unit) = synchronized(gate) {
        check(!constructing && !constructionFinished && !released && !terminal && retirementAccounting == null) {
            "Original retirement accounting unavailable"
        }
        retirementAccounting = accounting
        retirementAccountingReturned = false
    }

    /** Bounded manifest bookkeeping after this child's strict accounting actually returned. */
    internal fun onOwningRetirement(accounting: () -> Unit, publication: () -> Unit, group: ReleaseRetirementGroup) = synchronized(gate) {
        check(!constructing && !constructionFinished && !released && !terminal && owningRetirement == null) {
            "Original owning retirement unavailable"
        }
        owningRetirement = accounting
        owningRetirementReturned = false
        owningPublication = publication
        owningPublicationReturned = false
        retirementGroup = group
        groupReturned = false
    }

    /** Only bounded owning-registry bookkeeping; never client release code. */
    internal fun onSuccessfulRetirement(accounting: () -> Unit) {
        val notification = synchronized(gate) {
            check(retirement == null) { "Duplicate retirement accounting" }
            retirement = accounting
            notificationLocked()
        }
        notification?.invoke()
    }

    private fun notificationLocked(): (() -> Unit)? {
        if (!terminal || failure != null || !retirementAccountingReturned || !owningPublicationReturned || !groupReturned || retirementNotified || retirement == null) return null
        retirementNotified = true
        return retirement
    }

    internal fun owns(child: AutoCloseable): Boolean = synchronized(gate) { resource === child }
    internal fun ownsActual(child: Any): Boolean = synchronized(gate) {
        resource === child || (resource as? ReservedValue<*>)?.owns(child) == true
    }


    internal fun verifyResult(child: AutoCloseable) {
        val attached = synchronized(gate) {
            if (resource == null) {
                check(constructing && !constructionFinished)
                resource = child
                false
            } else resource === child
        }
        dispatchIfReady()
        check(attached) { "Factory result was not attached to its original ticket" }
    }

    /** Attach the child immediately after creation, before any further blocking/throwing step. */
    fun <T : AutoCloseable> construct(factory: ((AutoCloseable) -> Unit) -> T): T {
        beginConstruction()
        try {
            val result = factory(::attachReserved)
            val attached = synchronized(gate) {
                if (resource == null) {
                    // Reject the malformed factory, but keep its known actual result under the
                    // already funded release obligation. Never recycle it as an empty factory.
                    resource = result
                    false
                } else resource === result
            }
            synchronized(gate) {
                check(attached) { "Factory result was not attached to its original ticket" }
                check(!released) { "Factory reservation revoked" }
            }
            return result
        } catch (problem: Throwable) {
            release()
            throw problem
        } finally {
            endConstruction()
        }
    }

    internal fun beginConstruction() = synchronized(gate) {
        check(failure == null && slot.available) { "Original release worker unavailable" }
        check(!released && !constructing && !constructionFinished) { "Release reservation unavailable" }
        constructing = true
    }

    internal fun endConstruction() {
        synchronized(gate) { constructionFinished = true }
        finishIfReady()
    }

    internal fun requireAttachmentAdmission() = synchronized(gate) {
        check(failure == null && slot.available && constructing && !constructionFinished && resource == null) {
            "Original child attachment unavailable"
        }
    }

    internal fun attachReserved(child: AutoCloseable) {
        synchronized(gate) {
            check(constructing && !constructionFinished && resource == null) { "Unreserved child attachment" }
            resource = child
        }
        dispatchIfReady()
    }

    /** No resource callback executes on this calling thread, including stale factory cleanup. */
    fun release() {
        synchronized(gate) {
            released = true
            if (!constructing) constructionFinished = true
        }
        dispatchIfReady()
        finishIfReady()
    }

    internal fun workerFailed() = synchronized(gate) {
        if (!terminal) failure = IllegalStateException("Original release worker terminated")
    }

    private fun dispatchIfReady() {
        synchronized(gate) {
            if (!released || dispatched || resource == null) return
            dispatched = true
        }
        try { slot.submit(invocation) }
        catch (problem: Throwable) { synchronized(gate) { failure = problem } }
    }

    private fun invokeRelease() {
        val child = synchronized(gate) { checkNotNull(resource) }
        try {
            if (child is AcknowledgedCloseable) {
                child.closeAcknowledged().whenComplete(acknowledgement)
            } else {
                child.close()
                synchronized(gate) { acknowledged = true }
            }
        } catch (problem: Throwable) { synchronized(gate) { failure = problem } }
    }

    private fun releaseReturned() {
        synchronized(gate) { invocationReturned = true }
        finishIfReady()
    }

    private fun finishIfReady() {
        val recycle = synchronized(gate) {
            if (terminal || failure != null || !released || !constructionFinished) return
            if (resource != null && (!invocationReturned || !acknowledged)) return
            terminal = true
            resource = null
            true
        }
        if (recycle) {
            try {
                retirementAccounting?.invoke()
                synchronized(gate) { retirementAccountingReturned = true }
                owningRetirement?.invoke()
                synchronized(gate) { owningRetirementReturned = true }
                // Final publication is a separate bounded metadata phase, after the owning
                // callback returned. It cannot run a client factory/release/completion hook.
                owningPublication?.invoke()
                synchronized(gate) { owningPublicationReturned = true }
                // All fallible child callbacks have returned before this allocation-free
                // arrival. Only the last arrival runs the original aggregate owner hooks.
                retirementGroup?.childReady()
                synchronized(gate) { groupReturned = true }
            }
            catch (problem: Throwable) {
                synchronized(gate) { failure = problem }
                return
            }
            try {
                slot.recycle(this)
                // A normal close awaits this exact original's actual marker and slot
                // return, not merely its earlier successful owning-accounting publication.
                retirementGroup?.childFinished()
            } catch (problem: Throwable) {
                synchronized(gate) { failure = problem }
                // Never permit new owners to reuse capacity after failed final bookkeeping.
                retirementPool.failRetirementAdmission()
                return
            }
            synchronized(gate) { notificationLocked() }?.invoke()
        }
    }
}
