package uk.co.traynor.privategallery.core.security

import java.util.UUID
import kotlinx.coroutines.Job

/** Fixed container identities; neither identity selects a root or key factory. */
class ContainerId private constructor() {
    companion object {
        val PRIMARY = ContainerId()
        val SECONDARY = ContainerId()
        internal fun synthetic() = ContainerId()
    }
}
enum class PrimaryScope { READ, WRITE, EGRESS, CREDENTIALS, BACKUP, LOCAL_EDIT, REMOTE_AI_EGRESS, BROWSER_UPLOAD_EGRESS, HOLD_RESTORE }
data class SessionEpoch internal constructor(val value: UUID)
data class OperationId internal constructor(val value: UUID)
data class ScopedItemHandle internal constructor(
    val containerId: ContainerId,
    val epoch: SessionEpoch,
    val itemId: String,
    val revision: String,
)
data class ScopedCollectionHandle internal constructor(
    val containerId: ContainerId,
    val epoch: SessionEpoch,
    val collectionId: String,
    val revision: Long,
)
data class ScopedCacheIdentity internal constructor(
    val containerId: ContainerId,
    val itemId: String,
    val revision: String,
) {
    /** Primary cache namespace only; no foreign root can be selected from this identifier. */
    internal val primaryName: String get() {
        check(containerId === ContainerId.PRIMARY)
        return "primary:$itemId"
    }
}

/** In-process Primary authority. Identifiers alone never authorize decryption or promotion. */
class PrimarySessionAuthority(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val gate = Any()
    private val ioReleasePool = ProcessReleaseCapacity.primaryIo
    private val presentationReleasePool = ProcessReleaseCapacity.primaryPresentation
    private var epoch: SessionEpoch? = null
    private var key: ByteArray? = null
    private var deadline: Long? = null
    private val operations = mutableSetOf<PrimaryOperation>()
    private val reservedResources = mutableSetOf<ReleaseReservation>()
    private val reservedSessions = mutableSetOf<ReleaseReservation>()
    private var pendingOwnedRetirements = 0
    private val sessionResources = mutableSetOf<AutoCloseable>()
    private val unfinishedJobs = mutableSetOf<Job>()
    private val cleanupQueue = ArrayDeque<Cleanup>()
    private var pendingCleanup = 0
    private var cleanupFailed = false

    val cleanupComplete: Boolean get() = locked { cleanupCompleteLocked() }

    /** Transfers ownership, including when opening is rejected. Never restore this key after death. */
    fun open(key: ByteArray) {
        try {
            require(key.size == 32) { "Invalid Primary key" }
            revoke()
            locked {
                check(epoch == null && cleanupCompleteLocked()) { "Primary cleanup incomplete" }
                this.key = key
                epoch = SessionEpoch(UUID.randomUUID())
                deadline = null
            }
        } catch (failure: Throwable) { key.fill(0); throw failure }
    }

    fun operationOrNull(scopes: Set<PrimaryScope> = setOf(PrimaryScope.READ)): PrimaryOperation? = locked {
        expireLocked()
        epoch?.let { issueLocked(it, scopes) }
    }

    /** UI callbacks retain epoch ownership without retaining a copied media key. */
    fun bindingOrNull(): PrimarySessionBinding? = locked {
        expireLocked()
        epoch?.let { PrimarySessionBinding(this, it) }
    }

    internal fun bindingCurrent(binding: PrimarySessionBinding): Boolean = locked {
        expireLocked()
        !binding.closed && epoch == binding.epoch
    }

    internal fun <T> bind(binding: PrimarySessionBinding, action: () -> T): T = locked {
        expireLocked()
        check(!binding.closed && epoch == binding.epoch) { "Primary callback unavailable" }
        action()
    }

    internal fun issue(binding: PrimarySessionBinding, scopes: Set<PrimaryScope>): PrimaryOperation =
        bind(binding) { issueLocked(binding.epoch, scopes) }

    fun revoke() = locked { revokeLocked() }

    fun onBackgrounded(timeoutMillis: Long) = locked {
        require(timeoutMillis >= 0)
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

    internal fun isCurrent(operation: PrimaryOperation): Boolean = locked {
        expireLocked()
        epoch == operation.epoch
    }

    internal fun <T> authorized(operation: PrimaryOperation, requireLease: Boolean, action: () -> T): T = locked {
        expireLocked()
        check(epoch == operation.epoch && (!requireLease || !operation.closed)) { "Primary operation unavailable" }
        action()
    }

    internal fun fork(operation: PrimaryOperation, scopes: Set<PrimaryScope>): PrimaryOperation = authorized(operation, true) {
        check(operation.scopes.containsAll(scopes)) { "Primary capability escalation denied" }
        issueLocked(operation.epoch, scopes)
    }

    internal fun <T : AutoCloseable> createOwned(
        operation: PrimaryOperation,
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

    internal fun transferToSession(operation: PrimaryOperation, owned: OwnedResource<*>) = authorized(operation, true) {
        val reservation = owned.original
        check(reservation in operation.reservations && !reservation.retiring) { "Original resource transfer unavailable" }
        reservedSessions.add(reservation)
        operation.reservations.remove(reservation)
    }

    internal fun own(operation: PrimaryOperation, resource: AutoCloseable) {
        try { authorized(operation, true) { operation.resources.add(resource) } }
        catch (failure: Throwable) {
            locked { enqueueLocked(Cleanup(listOf(resource), emptyList())) }
            throw failure
        }
    }

    internal fun ownForSession(operation: PrimaryOperation, resource: AutoCloseable) {
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

    internal fun own(operation: PrimaryOperation, job: Job) {
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

    internal fun close(operation: PrimaryOperation) = locked { closeLocked(operation) }

    private fun issueLocked(epoch: SessionEpoch, scopes: Set<PrimaryScope>): PrimaryOperation =
        PrimaryOperation(this, epoch, checkNotNull(key).copyOf(), java.util.Collections.unmodifiableSet(scopes.toSet())).also(operations::add)

    private fun expireLocked() {
        if (deadline?.let { clock() >= it } == true) revokeLocked()
    }

    private fun revokeLocked(): Unit = retirementDispatch {
        epoch = null // Revoke admission before cancellation, descriptors, callbacks or wiping.
        deadline = null
        key?.fill(0); key = null
        operations.toList().forEach(::closeLocked)
        enqueueLocked(Cleanup(sessionResources.toList(), emptyList()))
        sessionResources.clear()
        reservedSessions.toList().forEach { it.release() }
    }

    private fun closeLocked(operation: PrimaryOperation) {
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
class PrimarySessionBinding internal constructor(private val authority: PrimarySessionAuthority, val epoch: SessionEpoch) : AutoCloseable {
    val containerId = ContainerId.PRIMARY
    @Volatile internal var closed = false
    val isCurrent: Boolean get() = authority.bindingCurrent(this)
    fun <T> commit(action: () -> T): T = authority.bind(this, action)
    fun operation(scopes: Set<PrimaryScope>): PrimaryOperation = authority.issue(this, scopes)
    override fun close() { closed = true }
}

/** One immutable epoch and a closeable copied-key lease. Publication needs epoch ownership only. */
class PrimaryOperation internal constructor(
    private val authority: PrimarySessionAuthority,
    val epoch: SessionEpoch,
    internal val key: ByteArray,
    internal val scopes: Set<PrimaryScope>,
) : AutoCloseable {
    val containerId = ContainerId.PRIMARY
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
    fun requireScope(scope: PrimaryScope) = commit {
        check(scope in scopes) { "Primary capability unavailable" }
    }
    fun fork(scopes: Set<PrimaryScope> = this.scopes): PrimaryOperation = authority.fork(this, scopes)
    fun handle(itemId: String, revision: String = "legacy"): ScopedItemHandle = commit {
        require(itemId.isNotEmpty() && revision.isNotEmpty())
        ScopedItemHandle(containerId, epoch, itemId, revision)
    }
    fun validate(handle: ScopedItemHandle) = commit {
        check(handle.containerId == containerId && handle.epoch == epoch) { "Primary item unavailable" }
    }
    fun collectionHandle(collectionId: String, revision: Long): ScopedCollectionHandle = commit {
        require(collectionId.isNotEmpty())
        ScopedCollectionHandle(containerId, epoch, collectionId, revision)
    }
    fun validate(handle: ScopedCollectionHandle) = commit {
        check(handle.containerId === containerId && handle.epoch == epoch) { "Primary collection unavailable" }
    }
    /** Validate identity before even invoking a storage lookup; recheck before delivery. */
    fun <T> resolve(handle: ScopedItemHandle, revision: () -> String, action: () -> T): T {
        requireScope(PrimaryScope.READ)
        validate(handle)
        check(revision() == handle.revision) { "Primary item revision changed" }
        checkValid()
        return action().also { checkValid() }
    }
    fun cacheIdentity(handle: ScopedItemHandle, presentationRevision: String = ""): ScopedCacheIdentity {
        requireScope(PrimaryScope.READ)
        validate(handle)
        return ScopedCacheIdentity(handle.containerId, handle.itemId, handle.revision + ":" + presentationRevision)
    }
    override fun close() = authority.close(this)
}
