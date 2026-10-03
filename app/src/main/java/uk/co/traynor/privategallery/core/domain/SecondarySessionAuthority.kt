package uk.co.traynor.privategallery.core.domain

import java.util.UUID
import uk.co.traynor.privategallery.core.security.ReleaseReservation
import uk.co.traynor.privategallery.core.security.ProcessReleaseCapacity
import uk.co.traynor.privategallery.core.security.OwnedFactoryScope
import uk.co.traynor.privategallery.core.security.OwnedResourceManifest
import uk.co.traynor.privategallery.core.security.OwnedResource
import uk.co.traynor.privategallery.core.security.ReservedJobRelease
import kotlinx.coroutines.Job
import uk.co.traynor.privategallery.core.security.ContainerId
import uk.co.traynor.privategallery.core.security.SessionEpoch
import uk.co.traynor.privategallery.core.security.OperationId

/** Foundation metadata and security capabilities only; no media or network capability. */
enum class SecondaryScope { READ, WRITE, CREDENTIALS, RECOVERY }

@ConsistentCopyVisibility
data class SecondaryRecordHandle internal constructor(
    val containerId: ContainerId,
    val epoch: SessionEpoch,
    val recordId: String,
    val revision: Long,
)

/** Opaque in-process callback admission, consumed once and invalidated on lifecycle changes. */
class SecondaryAuthAttempt internal constructor() {
    val id: UUID = UUID.randomUUID()
}

/**
 * In-process Secondary authority. Identifiers alone never authorize decryption or promotion.
 * [clock] must provide monotonic elapsed milliseconds including suspend/deep sleep;
 * Android owners must inject SystemClock.elapsedRealtime, never an awake-time clock.
 */
class SecondarySessionAuthority(private val clock: () -> Long) {
    private val gate = Any()
    private val ioReleasePool = ProcessReleaseCapacity.hiddenIo
    private val presentationReleasePool = ProcessReleaseCapacity.hiddenPresentation
    private var epoch: SessionEpoch? = null
    private var pendingAttempt: SecondaryAuthAttempt? = null
    private var key: ByteArray? = null
    private var deadline: Long? = null
    private val operations = mutableSetOf<SecondaryOperation>()
    private val reservedResources = mutableSetOf<ReleaseReservation>()
    private val reservedSessions = mutableSetOf<ReleaseReservation>()
    private var pendingOwnedRetirements = 0
    private val sessionResources = mutableSetOf<AutoCloseable>()
    private val unfinishedJobs = mutableSetOf<Job>()
    private val cleanupQueue = ArrayDeque<Cleanup>()
    private var pendingCleanup = 0
    private var cleanupFailed = false

    val cleanupComplete: Boolean get() = locked { cleanupCompleteLocked() }

    /** Begin only after the transient route is admitted; never unwraps or generates keys. */
    fun beginAuthentication(): SecondaryAuthAttempt = locked {
        revokeLocked()
        SecondaryAuthAttempt().also { pendingAttempt = it }
    }

    /** Cancellation from an older callback must not cancel a newer attempt. */
    fun cancelAuthentication(attempt: SecondaryAuthAttempt) = locked {
        if (pendingAttempt === attempt) pendingAttempt = null
    }

    /** Preparation check only; final promotion must use [commitAuthentication]. */
    fun checkAuthentication(attempt: SecondaryAuthAttempt) = locked {
        checkAuthenticationLocked(attempt)
    }

    /**
     * Serializes a short final metadata/pointer promotion with cancellation and revocation.
     * Prepare, derive and verify outside this gate. This neither consumes the attempt nor
     * grants a session/key; successful independent authentication is still required.
     */
    fun <T> commitAuthentication(attempt: SecondaryAuthAttempt, action: () -> T): T = locked {
        checkAuthenticationLocked(attempt)
        action()
    }

    private fun checkAuthenticationLocked(attempt: SecondaryAuthAttempt) {
        check(pendingAttempt === attempt && epoch == null) { "Secondary authentication unavailable" }
        check(cleanupCompleteLocked()) { "Secondary cleanup incomplete" }
    }

    /**
     * Takes key ownership on every path. The caller must verify the independent credential,
     * authenticated catalog and (for biometric) envelope/active-slot authorization first.
     * A callback success alone is never proof. Stale attempts return false and wipe the key.
     */
    fun completeAuthentication(attempt: SecondaryAuthAttempt, key: ByteArray): Boolean {
        try {
            return locked {
                if (pendingAttempt !== attempt) {
                    key.fill(0)
                    false
                } else {
                    pendingAttempt = null
                    require(key.size == 32) { "Invalid Secondary key" }
                    check(epoch == null && cleanupCompleteLocked()) { "Secondary cleanup incomplete" }
                    val nextEpoch = SessionEpoch(UUID.randomUUID())
                    this.key = key
                    epoch = nextEpoch
                    deadline = null
                    true
                }
            }
        } catch (failure: Throwable) { key.fill(0); throw failure }
    }

    fun operationOrNull(scopes: Set<SecondaryScope> = setOf(SecondaryScope.READ)): SecondaryOperation? = locked {
        expireLocked()
        epoch?.let { issueLocked(it, scopes) }
    }

    /** UI callbacks retain epoch ownership without retaining a copied master key. */
    fun bindingOrNull(): SecondarySessionBinding? = locked {
        expireLocked()
        epoch?.let { SecondarySessionBinding(this, it) }
    }

    internal fun bindingCurrent(binding: SecondarySessionBinding): Boolean = locked {
        expireLocked()
        !binding.closed && epoch == binding.epoch
    }

    internal fun <T> bind(binding: SecondarySessionBinding, action: () -> T): T = locked {
        expireLocked()
        check(!binding.closed && epoch == binding.epoch) { "Secondary callback unavailable" }
        action()
    }

    internal fun issue(binding: SecondarySessionBinding, scopes: Set<SecondaryScope>): SecondaryOperation =
        bind(binding) { issueLocked(binding.epoch, scopes) }

    fun revoke() = locked { revokeLocked() }

    fun onScreenOff() = revoke()

    fun onBackgrounded(timeoutMillis: Long) = locked {
        require(timeoutMillis >= 0)
        pendingAttempt = null
        expireLocked()
        if (epoch != null) {
            if (timeoutMillis == 0L) revokeLocked()
            else {
                val now = clock()
                val next = if (now > Long.MAX_VALUE - timeoutMillis) Long.MAX_VALUE else now + timeoutMillis
                // Repeated lifecycle delivery must never extend an already running deadline.
                deadline = deadline?.let { minOf(it, next) } ?: next
            }
        }
    }

    fun onForegrounded() = locked {
        expireLocked()
        if (epoch != null) deadline = null
    }

    internal fun expireIfNeeded() = locked { expireLocked() }

    internal fun isCurrent(operation: SecondaryOperation): Boolean = locked {
        expireLocked()
        epoch == operation.epoch
    }

    internal fun <T> authorized(operation: SecondaryOperation, requireLease: Boolean, action: () -> T): T = locked {
        expireLocked()
        check(epoch == operation.epoch && (!requireLease || !operation.closed)) { "Secondary operation unavailable" }
        action()
    }

    internal fun fork(operation: SecondaryOperation, scopes: Set<SecondaryScope>): SecondaryOperation = authorized(operation, true) {
        check(operation.scopes.containsAll(scopes)) { "Secondary capability escalation denied" }
        issueLocked(operation.epoch, scopes)
    }

    internal fun <T : AutoCloseable> createOwned(
        operation: SecondaryOperation,
        forSession: Boolean,
        manifest: OwnedResourceManifest,
        factory: OwnedFactoryScope.() -> T,
    ): OwnedResource<T> {
        authorized(operation, true) { }
        // Fund physical release capacity outside the ranked authority gate. No factory
        // may run until the SAME original operation passes the final registration check.
        val pool = if (manifest.presentation) presentationReleasePool else ioReleasePool
        val tickets = pool.reserveAll(operation, manifest.children.size)
        val reservation = try { ReleaseReservation(tickets) } catch (failure: Throwable) {
            tickets.forEach { it.release() }
            throw failure
        }
        var registered = false
        try {
            reservation.onRetirementAccounting(accounting = {
                synchronized(gate) {
                    try {
                        reservedResources.remove(reservation)
                        reservedSessions.remove(reservation)
                        operation.reservations.remove(reservation)
                    } catch (failure: Throwable) {
                        cleanupFailed = true
                        throw failure
                    }
                }
            }, onAcknowledged = {
                synchronized(gate) {
                    if (registered) {
                        pendingOwnedRetirements--
                        registered = false
                    }
                }
            })
            authorized(operation, true) {
                try {
                    reservedResources.add(reservation)
                    if (forSession) reservedSessions.add(reservation) else operation.reservations.add(reservation)
                    pendingOwnedRetirements++
                    registered = true
                } catch (failure: Throwable) {
                    reservedResources.remove(reservation)
                    reservedSessions.remove(reservation)
                    operation.reservations.remove(reservation)
                    throw failure
                }
            }
        } catch (failure: Throwable) {
            reservation.release()
            throw failure
        }
        try {
            val result = reservation.construct {
                factory(OwnedFactoryScope(this, manifest)).also { child ->
                    verifyResult(0, child)
                }
            }
            authorized(operation, true) { }
            return OwnedResource(result, reservation)
        } catch (failure: Throwable) {
            reservation.release()
            throw failure
        }
    }

    internal fun transferToSession(operation: SecondaryOperation, owned: OwnedResource<*>) = authorized(operation, true) {
        val reservation = owned.original
        check(reservation in operation.reservations && !reservation.retiring) { "Original resource transfer unavailable" }
        reservedSessions.add(reservation)
        operation.reservations.remove(reservation)
    }

    internal fun own(operation: SecondaryOperation, resource: AutoCloseable) {
        try { authorized(operation, true) { operation.resources.add(resource) } }
        catch (failure: Throwable) {
            locked { enqueueLocked(Cleanup(listOf(resource), emptyList())) }
            throw failure
        }
    }

    internal fun ownForSession(operation: SecondaryOperation, resource: AutoCloseable) {
        try {
            authorized(operation, false) {
                operation.resources.remove(resource) // Transfer a prepared resource out of the key lease.
                sessionResources.add(resource)
            }
        } catch (failure: Throwable) {
            locked { enqueueLocked(Cleanup(listOf(resource), emptyList())) }
            throw failure
        }
    }

    internal fun own(operation: SecondaryOperation, job: Job) {
        try {
            authorized(operation, true) {
                operation.jobs.add(job)
                unfinishedJobs.add(job)
                job.invokeOnCompletion { synchronized(gate) { unfinishedJobs.remove(job) } }
            }
        } catch (failure: Throwable) {
            locked {
                unfinishedJobs.add(job)
                job.invokeOnCompletion { synchronized(gate) { unfinishedJobs.remove(job) } }
                enqueueLocked(Cleanup(emptyList(), listOf(job)))
            }
            throw failure
        }
    }

    internal fun close(operation: SecondaryOperation) = locked { closeLocked(operation) }

    private fun issueLocked(epoch: SessionEpoch, scopes: Set<SecondaryScope>): SecondaryOperation =
        SecondaryOperation(this, epoch, checkNotNull(key).copyOf(), java.util.Collections.unmodifiableSet(scopes.toSet())).also(operations::add)

    private fun expireLocked() {
        if (deadline?.let { clock() >= it } == true) revokeLocked()
    }

    private fun revokeLocked(): Unit = retirementDispatch {
        pendingAttempt = null
        epoch = null // Revoke admission before cancellation, descriptors, callbacks or wiping.
        deadline = null
        key?.fill(0); key = null
        operations.toList().forEach(::closeLocked)
        enqueueLocked(Cleanup(sessionResources.toList(), emptyList()))
        sessionResources.clear()
        reservedSessions.toList().forEach { it.release() }
    }

    private fun closeLocked(operation: SecondaryOperation) {
        if (operation.closed) return
        retirementDispatch {
            operation.closed = true
            operation.key.fill(0)
            operations.remove(operation)
            operation.reservations.toList().forEach { it.release() }
            enqueueLocked(Cleanup(operation.resources.toList(), operation.jobs.toList()))
            operation.resources.clear(); operation.jobs.clear()
        }
    }

    /** Pool-visible before any fallible snapshot; failure pins this partition for the process. */
    private inline fun <T> retirementDispatch(action: () -> T): T {
        ioReleasePool.beginRetirementDispatch()
        presentationReleasePool.beginRetirementDispatch()
        try {
            return action()
        } catch (failure: Throwable) {
            cleanupFailed = true
            ioReleasePool.failRetirementAdmission()
            presentationReleasePool.failRetirementAdmission()
            throw failure
        } finally {
            presentationReleasePool.endRetirementDispatch()
            ioReleasePool.endRetirementDispatch()
        }
    }

    private fun enqueueLocked(cleanup: Cleanup) {
        if (cleanup.resources.isEmpty() && cleanup.jobs.isEmpty()) return
        pendingCleanup++
        cleanupQueue.addLast(cleanup)
    }

    private fun cleanupCompleteLocked() =
        !ioReleasePool.hasUnacknowledgedRetirement && !presentationReleasePool.hasUnacknowledgedRetirement &&
        pendingOwnedRetirements == (if (epoch == null) 0 else reservedResources.count { !it.retiring }) && pendingCleanup == 0 && unfinishedJobs.isEmpty() && !cleanupFailed && reservedResources.none { !it.successful && (epoch == null || it.retiring) }

    private fun drainCleanup() {
        while (true) {
            val cleanup = synchronized(gate) { cleanupQueue.removeFirstOrNull() } ?: return
            try {
                cleanup.jobs.forEach { job ->
                    try { job.cancel() } catch (_: Throwable) { synchronized(gate) { cleanupFailed = true } }
                }
                cleanup.resources.forEach { resource ->
                    try { resource.close() } catch (_: Throwable) { synchronized(gate) { cleanupFailed = true } }
                }
            } finally { synchronized(gate) { pendingCleanup-- } }
        }
    }

    private inline fun <T> locked(action: () -> T): T = try { synchronized(gate) { action() } }
        finally { if (!Thread.holdsLock(gate)) drainCleanup() }

    private data class Cleanup(val resources: List<AutoCloseable>, val jobs: List<Job>)
}

/** Admission to an original epoch; this object contains no key and performs no protected IO. */
class SecondarySessionBinding internal constructor(private val authority: SecondarySessionAuthority, val epoch: SessionEpoch) : AutoCloseable {
    val containerId = ContainerId.SECONDARY
    @Volatile internal var closed = false
    val isCurrent: Boolean get() = authority.bindingCurrent(this)
    fun <T> commit(action: () -> T): T = authority.bind(this, action)
    fun operation(scopes: Set<SecondaryScope>): SecondaryOperation = authority.issue(this, scopes)
    override fun close() { closed = true }
}

/** One immutable epoch and a closeable copied-key lease. Publication needs epoch ownership only. */
class SecondaryOperation internal constructor(
    private val authority: SecondarySessionAuthority,
    val epoch: SessionEpoch,
    internal val key: ByteArray,
    internal val scopes: Set<SecondaryScope>,
) : AutoCloseable {
    val containerId = ContainerId.SECONDARY
    val operationId = OperationId(UUID.randomUUID())
    internal var closed = false
    internal val reservations = mutableSetOf<ReleaseReservation>()
    internal val resources = mutableSetOf<AutoCloseable>()
    internal val jobs = mutableSetOf<Job>()
    val isCurrent: Boolean get() = authority.isCurrent(this)
    fun checkValid() { authority.authorized(this, true) {} }
    /** Keep actions short: final authorization and metadata promotion, never expensive preparation. */
    fun <T> commit(action: () -> T): T = authority.authorized(this, true, action)
    fun <T> publish(action: () -> T): T = authority.authorized(this, false, action)
    fun <T : AutoCloseable> createOwned(factory: ((T) -> Unit) -> T): OwnedResource<T> =
        createOwned(OwnedResourceManifest.io("resource")) { factory { child -> attach("resource", child) } }
    fun <T : AutoCloseable> createSessionOwned(factory: ((T) -> Unit) -> T): OwnedResource<T> =
        createSessionOwned(OwnedResourceManifest.io("resource")) { factory { child -> attach("resource", child) } }
    fun <T : AutoCloseable> createOwned(manifest: OwnedResourceManifest, factory: OwnedFactoryScope.() -> T): OwnedResource<T> =
        authority.createOwned(this, false, manifest, factory)
    fun <T : AutoCloseable> createSessionOwned(manifest: OwnedResourceManifest, factory: OwnedFactoryScope.() -> T): OwnedResource<T> =
        authority.createOwned(this, true, manifest, factory)
    fun transferToSession(owned: OwnedResource<*>) = authority.transferToSession(this, owned)
    /** Factory must create a lazy protected child (or an inert root), never start user work. */
    fun <T : Job> createOwnedJob(factory: ((T) -> Unit) -> T): T {
        var actual: T? = null
        val owned = createOwned<ReservedJobRelease> { attach ->
            ReservedJobRelease().also { release ->
                attach(release)
                release.create { attachJob -> factory { child -> actual = child; attachJob(child) } }
            }
        }
        try {
            owned.value.retireOnCompletion(owned::close)
            return checkNotNull(actual)
        } catch (failure: Throwable) {
            owned.close()
            throw failure
        }
    }
    fun <T : AutoCloseable> own(resource: T): T = resource.also { authority.own(this, it) }
    fun <T : Job> own(job: T): T = job.also { authority.own(this, it) }
    /** Handoff prepared UI resources without retaining this key lease; revoke still closes them. */
    fun <T : AutoCloseable> ownForSession(resource: T): T = resource.also { authority.ownForSession(this, it) }
    fun requireScope(scope: SecondaryScope) = commit {
        check(scope in scopes) { "Secondary capability unavailable" }
    }
    fun fork(scopes: Set<SecondaryScope> = this.scopes): SecondaryOperation = authority.fork(this, scopes)
    /** Metadata identity only; this API neither resolves media nor selects a storage root. */
    fun recordHandle(recordId: String, revision: Long): SecondaryRecordHandle = commit {
        require(recordId.isNotEmpty() && revision > 0)
        SecondaryRecordHandle(containerId, epoch, recordId, revision)
    }
    fun validate(handle: SecondaryRecordHandle) = commit {
        check(handle.containerId === containerId && handle.epoch == epoch) { "Secondary record unavailable" }
    }
    override fun close() = authority.close(this)
}
