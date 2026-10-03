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
    fun <T : AutoCloseable> attach(index: Int, child: T): T = synchronized(gate) {
        var other = 0
        while (other < tickets.size) {
            check(!tickets[other].ownsActual(child)) { "Actual resource already has its original token" }
            other++
        }
        check(!claimed[index]) { "Original child already claimed" }
        tickets[index].attachReserved(child)
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
        }
        // Until an actual child exists the original constructing ticket stays pending,
        // with its physical worker idle. No release callback waits for construction.
        val actual = factory()
        bindValue(index, release, actual)
        return release
    }
    private fun <T : Any> bindValue(index: Int, release: ReservedValue<T>, value: T) = synchronized(gate) {
        check(claimed[index] && nativeAdapters[index] === release) { "Original native adapter unavailable" }
        var other = 0
        while (other < tickets.size) {
            check(!tickets[other].ownsActual(value)) { "Actual resource already has its original token" }
            other++
        }
        release.bind(value)
        // Late revoke may already have retired this original ticket. Attach the actual
        // acquired value immediately, without allocation or a new admission requirement.
        tickets[index].attachReserved(release)
    }
    fun copyBytes(index: Int, source: ByteArray, readerMutex: Any? = null): ByteArray {
        val ticket = synchronized(gate) {
            check(!released && constructorThread === Thread.currentThread()) { "Original array factory unavailable" }
            check(!claimed[index]) { "Original child already claimed" }
            tickets[index].requireAttachmentAdmission()
            claimed[index] = true
            tickets[index]
        }
        // No application or reservation gate is held across allocation/copy.
        val release = allocateArrayRelease(readerMutex)
        release.requireEmpty()
        val actual = copyArray(source)
        release.bind(actual)
        // The original preclaimed ticket cannot acquire another child in between. No
        // callback/node allocation follows the actual copy; late revoke uses this ticket.
        ticket.attachReserved(release)
        return actual
    }
    fun verifyResult(index: Int, child: AutoCloseable) = synchronized(gate) {
        val original = tickets[index]
        check(!claimed[index] || original.owns(child)) { "Original child already claimed" }
        var other = 0
        while (other < tickets.size) {
            val ticket = tickets[other]
            check(ticket === original || !ticket.ownsActual(child)) { "Actual resource already has its original token" }
            other++
        }
        original.verifyResult(child)
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
