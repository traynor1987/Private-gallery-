package uk.co.traynor.privategallery.core.security

/** Original, bounded child manifest; no authority is granted by reservation alone. */
internal class ReleaseReservation(
    private val tickets: List<ReleaseTicket>,
    private val allocateArrayRelease: (Any?) -> CopiedArrayRelease = ::CopiedArrayRelease,
    private val copyArray: (ByteArray) -> ByteArray = { it.copyOf() },
) {
    private val retirementPool = tickets.first().retirementPool
    init { require(tickets.all { it.retirementPool === retirementPool }) }
    private val gate = Any()
    val retirement = RetirementAcknowledgement()
    val terminalRetirement = RetirementAcknowledgement()
    private val claimed = BooleanArray(tickets.size)
    private val phaseSealed = BooleanArray(tickets.size)
    // Keep original identity associations after physical phase slots are returned.
    private val actualIdentities = arrayOfNulls<Any>(tickets.size)
    private val factoryInProgress = BooleanArray(tickets.size)
    private val jobAdapters = arrayOfNulls<ReservedJobRelease>(tickets.size)
    private val nativeAdapters = arrayOfNulls<ReservedValue<*>>(tickets.size)
    private var constructorThread: Thread? = null
    private var constructionAdmitted = false
    private val retirementNotified = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var strictAccounting = false
    @Volatile private var released = false
    val retiring: Boolean get() = released
    val failed: Boolean get() = tickets.any { it.failed }
    val successful: Boolean get() = tickets.all { it.successful } && (!strictAccounting || retirement.isComplete)
    fun <T> construct(factory: ReleaseReservation.() -> T): T {
        // Repeated/reentrant rejection is not a failure of the original admitted constructor.
        synchronized(gate) {
            check(!released && !constructionAdmitted) { "Original factory reservation unavailable" }
            constructionAdmitted = true
            constructorThread = Thread.currentThread()
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
            synchronized(gate) { constructorThread = null }
            var index = 0
            while (index < begun) { tickets[index].endConstruction(); index++ }
        }
    }
    private fun hasIdentity(index: Int, value: Any): Boolean =
        actualIdentities[index] === value || nativeAdapters[index] === value ||
            jobAdapters[index] === value || tickets[index].ownsActual(value)

    fun <T : AutoCloseable> attach(index: Int, child: T): T = synchronized(gate) {
        var other = 0
        while (other < tickets.size) {
            check(!hasIdentity(other, child)) { "Actual resource already has its original token" }
            other++
        }
        check(!claimed[index]) { "Original child already claimed" }
        tickets[index].attachReserved(child)
        actualIdentities[index] = child
        claimed[index] = true
        child
    }
    internal fun <T : Any> createValue(index: Int, dispose: (T) -> Unit, factory: () -> T): ReservedValue<T> {
        val release = ReservedValue(dispose)
        synchronized(gate) {
            check(!released && constructorThread === Thread.currentThread()) { "Original native factory unavailable" }
            check(!claimed[index]) { "Original child already claimed" }
            tickets[index].requireAttachmentAdmission()
            claimed[index] = true
            nativeAdapters[index] = release
            factoryInProgress[index] = true
        }
        // Until an actual child exists the original constructing ticket stays pending,
        // with its physical worker idle. No release callback waits for construction.
        try {
            val actual = factory()
            bindValue(index, release, actual)
            return release
        } finally { synchronized(gate) { factoryInProgress[index] = false } }
    }
    private fun <T : Any> bindValue(index: Int, release: ReservedValue<T>, value: T) = synchronized(gate) {
        check(claimed[index] && nativeAdapters[index] === release) { "Original native adapter unavailable" }
        var other = 0
        while (other < tickets.size) {
            check(!hasIdentity(other, value)) { "Actual resource already has its original token" }
            other++
        }
        actualIdentities[index] = value
        release.bind(value)
        // Late revoke may already have retired this original ticket. Attach the actual
        // acquired value immediately, without allocation or a new admission requirement.
        tickets[index].attachReserved(release)
    }
    internal fun createJob(index: Int, factory: ((kotlinx.coroutines.Job) -> Unit) -> kotlinx.coroutines.Job): ReservedJobRelease {
        val release = ReservedJobRelease()
        synchronized(gate) {
            check(!released && constructorThread === Thread.currentThread()) { "Original Job factory unavailable" }
            check(!claimed[index]) { "Original child already claimed" }
            tickets[index].requireAttachmentAdmission()
            claimed[index] = true
            jobAdapters[index] = release
            factoryInProgress[index] = true
        }
        // The preclaimed worker stays idle until the actual Job is immediately bound.
        try { release.create(attachOriginal = { actual ->
            synchronized(gate) {
                check(jobAdapters[index] === actual) { "Original Job adapter unavailable" }
                var other = 0
                while (other < tickets.size) {
                    check(!hasIdentity(other, actual.actualJob)) { "Actual Job already has its original token" }
                    other++
                }
                actualIdentities[index] = actual.actualJob
                tickets[index].attachReserved(actual)
            }
        }, factory = factory)
            return release
        } finally { synchronized(gate) { factoryInProgress[index] = false } }
    }
    fun copyBytes(index: Int, source: ByteArray, readerMutex: Any? = null): ByteArray {
        val ticket = synchronized(gate) {
            check(!released && constructorThread === Thread.currentThread()) { "Original array factory unavailable" }
            check(!claimed[index]) { "Original child already claimed" }
            tickets[index].requireAttachmentAdmission()
            claimed[index] = true
            factoryInProgress[index] = true
            tickets[index]
        }
        // No application or reservation gate is held across allocation/copy.
        try {
            val release = allocateArrayRelease(readerMutex)
            release.requireEmpty()
            val actual = copyArray(source)
            synchronized(gate) {
                var other = 0
                while (other < tickets.size) {
                    check(!hasIdentity(other, actual)) { "Actual array already has its original token" }
                    other++
                }
                actualIdentities[index] = actual
                release.bind(actual)
                // The in-progress marker denies reentrant phase sealing until attachment.
                ticket.attachReserved(release)
            }
            return actual
        } finally { synchronized(gate) { factoryInProgress[index] = false } }
    }
    /** Typed input roots must already belong to this scope before generic result verification. */
    internal fun requireNativeRoot(root: ReservedValue<*>) = synchronized(gate) {
        check(!released && constructorThread === Thread.currentThread()) { "Original input factory unavailable" }
        check(claimed[0] && nativeAdapters[0] === root && actualIdentities[0] != null &&
            !factoryInProgress[0] && tickets[0].owns(root)) { "Input root absent from this original native factory" }
    }

    fun verifyResult(index: Int, child: AutoCloseable) = synchronized(gate) {
        val original = tickets[index]
        check(!claimed[index] || original.owns(child)) { "Original child already claimed" }
        var other = 0
        while (other < tickets.size) {
            val ticket = tickets[other]
            check(ticket === original || !hasIdentity(other, child)) { "Actual resource already has its original token" }
            other++
        }
        original.verifyResult(child)
    }
    /** Finish only declared secondary children; entry0 pins this enclosing factory. */
    internal fun retirePhase(indices: IntArray, absent: Boolean) {
        synchronized(gate) {
            check(!released && constructorThread === Thread.currentThread()) { "Original factory phase unavailable" }
            require(indices.isNotEmpty() && indices.size < tickets.size) { "Invalid original phase" }
            var position = 0
            while (position < indices.size) {
                val index = indices[position]
                require(index in tickets.indices) { "Child absent from original manifest" }
                check(index != 0) { "Original factory lifetime cannot retire inside construction" }
                check(!phaseSealed[index] && claimed[index] != absent && !factoryInProgress[index]) { "Original child phase unavailable" }
                if (!absent) check(actualIdentities[index] != null && tickets[index].hasAttachedResource) { "Original child construction incomplete" }
                var earlier = 0
                while (earlier < position) { require(indices[earlier] != index) { "Duplicate phase child" }; earlier++ }
                position++
            }
            // Every index was validated before any original child state changed.
            position = 0
            while (position < indices.size) {
                val index = indices[position]
                phaseSealed[index] = true
                if (absent) claimed[index] = true
                position++
            }
        }
        try {
            retirementPool.beginRetirementDispatch()
            try {
                // Dispatch the WHOLE phase before waiting: transport may unblock input close.
                var position = 0
                while (position < indices.size) { tickets[indices[position]].endConstruction(); position++ }
                position = 0
                while (position < indices.size) { tickets[indices[position]].release(); position++ }
            } finally { retirementPool.endRetirementDispatch() }
            var position = 0
            while (position < indices.size) {
                val ticket = tickets[indices[position]]
                do {
                    var candidate = 0
                    while (candidate < indices.size) {
                        if (tickets[indices[candidate]].failed) throw java.io.IOException("Original child release failed")
                        candidate++
                    }
                } while (!ticket.terminalRetirement.await(1, java.util.concurrent.TimeUnit.SECONDS))
                check(ticket.successful) { "Original child terminal unavailable" }
                position++
            }
        } catch (problem: Throwable) {
            // A failed phase cannot be caught and used to create the next native child.
            try { release() } catch (releaseProblem: Throwable) { problem.addSuppressed(releaseProblem) }
            throw problem
        }
    }

    fun owns(child: AutoCloseable): Boolean = tickets.any { it.owns(child) }
    /** Legacy observation may be late; it grants no owning retirement acknowledgement. */
    fun onSuccessfulRetirement(accounting: () -> Unit) {
        tickets.forEach { ticket -> ticket.onSuccessfulRetirement { if (successful && retirementNotified.compareAndSet(false, true)) accounting() } }
    }
    /** Register actual owning metadata before releasing or exposing the factory. */
    fun onRetirementAccounting(onAcknowledged: () -> Unit = {}, accounting: () -> Unit) {
        val group = ReleaseRetirementGroup(tickets.size, retirement, terminalRetirement, accounting, onAcknowledged)
        strictAccounting = true
        tickets.forEach { it.onRetirementAccounting {} }
        tickets.forEach { it.onOwningRetirement(accounting = {}, publication = {}, group = group) }
    }
    fun release() {
        // Process-visible before local revocation, including normal disposal outside
        // an authority. The counter never holds the pool gate across child dispatch.
        retirementPool.beginRetirementDispatch()
        try {
            synchronized(gate) { released = true }
            var index = 0
            while (index < tickets.size) { tickets[index].release(); index++ }
        } catch (failure: Throwable) {
            retirementPool.failRetirementAdmission()
            throw failure
        } finally { retirementPool.endRetirementDispatch() }
    }
}
