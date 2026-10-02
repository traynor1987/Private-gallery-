package uk.co.traynor.privategallery.core.security

/** Original, bounded child manifest; no authority is granted by reservation alone. */
internal class ReleaseReservation(private val tickets: List<ReleaseTicket>) {
    private val gate = Any()
    private var constructionAdmitted = false
    private val retirementNotified = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var released = false
    val retiring: Boolean get() = released
    val failed: Boolean get() = tickets.any { it.failed }
    val successful: Boolean get() = tickets.all { it.successful }
    fun <T> construct(factory: ReleaseReservation.() -> T): T {
        // Repeated/reentrant rejection is not a failure of the original admitted constructor.
        synchronized(gate) {
            check(!released && !constructionAdmitted) { "Original factory reservation unavailable" }
            constructionAdmitted = true
        }
        var begun = 0
        try {
            // Pin ALL unused entries too: revoke cannot recycle a constructing factory's budget.
            while (begun < tickets.size) { tickets[begun].beginConstruction(); begun++ }
            val result = factory()
            check(!released) { "Original factory reservation revoked" }
            return result
        } catch (failure: Throwable) {
            release()
            throw failure
        } finally {
            var index = 0
            while (index < begun) { tickets[index].endConstruction(); index++ }
        }
    }
    fun <T : AutoCloseable> attach(index: Int, child: T): T = synchronized(gate) {
        check(tickets.none { it.owns(child) }) { "Actual resource already has its original token" }
        tickets[index].attachReserved(child)
        child
    }
    fun verifyResult(index: Int, child: AutoCloseable) = tickets[index].verifyResult(child)
    fun owns(child: AutoCloseable): Boolean = tickets.any { it.owns(child) }
    fun onSuccessfulRetirement(accounting: () -> Unit) {
        tickets.forEach { ticket -> ticket.onSuccessfulRetirement { if (successful && retirementNotified.compareAndSet(false, true)) accounting() } }
    }
    fun release() {
        synchronized(gate) { released = true }
        tickets.forEach { it.release() }
    }
}
