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
    private var epoch: SessionEpoch? = null
    private var key: ByteArray? = null
    private var deadline: Long? = null
    private val operations = mutableSetOf<PrimaryOperation>()
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

    private fun revokeLocked() {
        epoch = null // Revoke admission before cancellation, descriptors, callbacks or wiping.
        deadline = null
        key?.fill(0); key = null
        operations.toList().forEach(::closeLocked)
        enqueueLocked(Cleanup(sessionResources.toList(), emptyList()))
        sessionResources.clear()
    }

    private fun closeLocked(operation: PrimaryOperation) {
        if (operation.closed) return
        operation.closed = true
        operation.key.fill(0)
        operations.remove(operation)
        enqueueLocked(Cleanup(operation.resources.toList(), operation.jobs.toList()))
        operation.resources.clear(); operation.jobs.clear()
    }

    private fun enqueueLocked(cleanup: Cleanup) {
        if (cleanup.resources.isEmpty() && cleanup.jobs.isEmpty()) return
        pendingCleanup++
        cleanupQueue.addLast(cleanup)
    }

    private fun cleanupCompleteLocked() = pendingCleanup == 0 && unfinishedJobs.isEmpty() && !cleanupFailed

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
    internal val resources = mutableSetOf<AutoCloseable>()
    internal val jobs = mutableSetOf<Job>()
    val isCurrent: Boolean get() = authority.isCurrent(this)
    fun checkValid() { authority.authorized(this, true) {} }
    /** Keep actions short: final authorization and metadata promotion, never expensive preparation. */
    fun <T> commit(action: () -> T): T = authority.authorized(this, true, action)
    fun <T> publish(action: () -> T): T = authority.authorized(this, false, action)
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
