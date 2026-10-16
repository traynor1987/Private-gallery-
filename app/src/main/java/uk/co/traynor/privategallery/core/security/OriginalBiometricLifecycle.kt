package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Each backend must acquire a fresh AndroidX Activity ViewModel, never a shared prompt host.
 * attach is called immediately after acquiring the host, before any further throwing work.
 * Events are direct Main callbacks. destroyed reports the final host onDestroy invocation;
 * the original then posts a return barrier. No event acknowledges Keystore-internal retirement. */
internal interface OriginalBiometricBackend {
    /** Complete app-owned graph retirement, not only Activity destruction or prompt cancel. */
    val supportsAcknowledgedRetirement: Boolean get() = false
    fun acquire(events: Events, attach: (Host) -> Unit)
    interface Host { fun cancel(); fun destroy() }
    interface Events { fun success(crypto: Any); fun error(); fun destroyed(); fun canAcquire(): Boolean }
}

/** Bounded single UI host admission. A replacement cannot acquire before actual retirement. */
internal class OriginalBiometricCoordinator(
    private val post: (Runnable) -> Boolean,
    private val isMain: () -> Boolean,
) {
    private var constructing = false
    private var activeOriginal: ReleaseReservation? = null
    fun begin(guard: ScopedIoGuard, expectedCrypto: Any, backend: OriginalBiometricBackend): OriginalBiometricLifecycle =
        begin(BiometricOriginalOwner.PrimaryCredentials(guard), expectedCrypto, backend)
    fun begin(owner: BiometricOriginalOwner, expectedCrypto: Any, backend: OriginalBiometricBackend): OriginalBiometricLifecycle {
        check(isMain()) { "Biometric admission requires Main" }
        check(backend.supportsAcknowledgedRetirement) { "Biometric app-owned retirement unavailable" }
        check(!constructing && activeOriginal?.terminalRetirement?.isComplete != false) { "Original biometric lifecycle incomplete" }
        owner.check()
        val state = OriginalBiometricState(post, isMain, expectedCrypto, owner)
        constructing = true
        try {
        val owned = owner.create(OwnedResourceManifest.io("biometric-bridge", "biometric-cancel", "biometric-callback", "biometric-host")) {
            attach("biometric-bridge", state.bridge)
            attach("biometric-cancel", state.cancel)
            attach("biometric-callback", state.callback)
            attach("biometric-host", state.hostLifetime)
            activeOriginal = original
            state.bindOriginal(original)
            try {
                owner.check()
                backend.acquire(state.events, state::attach)
                owner.check()
            } finally { state.acquisitionReturned() }
            state.bridge
        }
        return OriginalBiometricLifecycle(owner, owned, state)
        } finally { constructing = false }
    }
}

internal class OriginalBiometricLifecycle internal constructor(
    private val owner: BiometricOriginalOwner,
    private val owned: OwnedResource<out AutoCloseable>,
    private val state: OriginalBiometricState,
) : AutoCloseable {
    val retirement: RetirementAcknowledgement get() = owned.retirement
    val releaseFailed: Boolean get() = owned.releaseFailed
    val readyForAuthenticatedUse: Boolean get() = state.readyForAuthenticatedUse
    fun awaitAuthenticatedUse(timeout: Long, unit: TimeUnit): Boolean = state.awaitAuthenticatedUse(timeout, unit)
    /** Synchronous borrow by the caller's separately funded worker, never an SDK callback.
     * The trusted consumer must not retain the Cipher or start unjoined work. */
    fun runAuthenticated(action: (Any) -> Unit): Boolean {
        try { owner.check() }
        catch (_: IllegalStateException) { return false }
        val crypto = state.beginUse() ?: return false
        var completed = false
        try {
            action(crypto)
            try { owner.check(); completed = true }
            catch (_: IllegalStateException) { return false }
            return !state.cancelled
        } finally { state.endUse(completed) }
    }
    /** Receipt only: callers must still use the exact authority's final admission barrier. */
    fun takeSuccessReceipt(): Boolean {
        if (!retirement.isComplete || releaseFailed) return false
        try { owner.check() } catch (_: IllegalStateException) { return false }
        if (!state.claimSuccess()) return false
        return true
    }
    override fun close() { state.cancelSuccess(); owned.close() }
}

/** Four independent acknowledgement sources, all created before backend acquisition.
 * No app callback is invoked under the private state gate or any authority gate. */
internal class OriginalBiometricState(
    private val post: (Runnable) -> Boolean,
    private val isMain: () -> Boolean,
    expected: Any,
    owner: BiometricOriginalOwner,
) {
    private val gate = Any()
    private val readiness = CountDownLatch(1)
    private var acquisitionOwner: BiometricOriginalOwner? = owner
    private var expected: Any? = expected
    private var actual: OriginalBiometricBackend.Host? = null
    private var original: ReleaseReservation? = null
    private var acquiring = true
    private var retiring = false
    private var terminal = false
    private var success = false
    private var consumed = false
    private var used = false
    private var using = false
    private var cancelledState = false
    private var cancelPosted = false
    private var destroyPosted = false
    private var callbackPosted = false
    private var destroyBarrierPosted = false
    private var hostReturned = false
    private var hostDestroyed = false
    private var callbackReturned = false
    private var cancelReturned = false
    private val bridgeDone = CompletableFuture<Unit>()
    private val cancelDone = CompletableFuture<Unit>()
    private val callbackDone = CompletableFuture<Unit>()
    private val hostDone = CompletableFuture<Unit>()
    val bridge: AcknowledgedCloseable = object : AcknowledgedCloseable {
        override fun closeAcknowledged(): CompletionStage<Unit> { retire(); return bridgeDone }
    }
    val cancel: AcknowledgedCloseable = object : AcknowledgedCloseable {
        override fun closeAcknowledged(): CompletionStage<Unit> { retire(); return cancelDone }
    }
    val callback: AcknowledgedCloseable = object : AcknowledgedCloseable {
        override fun closeAcknowledged(): CompletionStage<Unit> { retire(); return callbackDone }
    }
    val hostLifetime: AcknowledgedCloseable = object : AcknowledgedCloseable {
        override fun closeAcknowledged(): CompletionStage<Unit> { retire(); return hostDone }
    }
    // Retained AndroidX callbacks keep only this bridge after destruction, never an epoch,
    // operation, Cipher or Activity. Clearing it is bounded and does not invoke user code.
    private class Bridge(var target: OriginalBiometricState?) : OriginalBiometricBackend.Events {
        override fun success(crypto: Any) { target?.terminal(crypto) }
        override fun error() { target?.terminal(null) }
        override fun destroyed() { target?.destroyed() }
        override fun canAcquire(): Boolean = target?.canAcquire() == true
    }
    private val eventBridge = Bridge(this)
    val events: OriginalBiometricBackend.Events get() = eventBridge
    private val cancelInvocation = Runnable {
        try {
            check(isMain())
            val host = synchronized(gate) { actual }
            host?.cancel()
            synchronized(gate) { cancelReturned = true }
            cancelDone.complete(Unit)
            finish()
        } catch (failure: Throwable) { cancelDone.completeExceptionally(failure) }
    }
    private val destroyInvocation = Runnable {
        try {
            check(isMain())
            val host = synchronized(gate) { if (hostDestroyed) null else actual }
            if (host == null) destroyed() else host.destroy()
        } catch (failure: Throwable) { hostDone.completeExceptionally(failure) }
    }
    private val callbackBarrier = Runnable {
        synchronized(gate) { callbackReturned = true }
        callbackDone.complete(Unit)
        readiness.countDown()
        finish()
    }
    private val destroyBarrier = Runnable {
        synchronized(gate) {
            hostReturned = true
            actual = null
            this@OriginalBiometricState.expected = null
            eventBridge.target = null
            acquisitionOwner = null
        }
        hostDone.complete(Unit)
        // No terminal callback is required when construction/cancellation destroyed the host.
        synchronized(gate) { callbackReturned = true }
        callbackDone.complete(Unit)
        finish()
    }
    private fun canAcquire(): Boolean {
        check(isMain())
        val owner = synchronized(gate) {
            if (retiring || cancelledState || hostDestroyed || original?.retiring != false) null else acquisitionOwner
        } ?: return false
        try { owner.check() } catch (_: IllegalStateException) { return false }
        return synchronized(gate) { !retiring && !cancelledState && !hostDestroyed && original?.retiring == false }
    }
    fun bindOriginal(value: ReleaseReservation) = synchronized(gate) {
        check(original == null && acquiring)
        original = value
    }
    fun attach(host: OriginalBiometricBackend.Host) = synchronized(gate) {
        check(acquiring && actual == null) { "Original biometric host already acquired" }
        actual = host
    }
    fun acquisitionReturned() {
        synchronized(gate) { acquiring = false }
        schedule()
    }
    private fun terminal(crypto: Any?) {
        check(isMain())
        val matched = synchronized(gate) {
            if (terminal || retiring) return
            terminal = true
            if (crypto != null && crypto === expected) true
            else { expected = null; retiring = true; false }
        }
        if (matched) {
            val dispatch = synchronized(gate) {
                if (callbackPosted) false else { callbackPosted = true; true }
            }
            if (dispatch) queuedCallback.enqueue()
        } else { original?.release(); schedule() }
    }
    private fun retire() {
        readiness.countDown()
        synchronized(gate) {
            retiring = true
            if (!using) expected = null
        }
        schedule()
    }
    private fun destroyed() {
        check(isMain())
        val unexpected = synchronized(gate) {
            hostDestroyed = true
            if (!retiring) { success = false; retiring = true; true } else false
        }
        if (unexpected) { original?.release(); schedule() }
        val dispatch = synchronized(gate) {
            if (destroyBarrierPosted) false else { destroyBarrierPosted = true; true }
        }
        if (dispatch) queuedDestroyBarrier.enqueue()
    }
    private fun schedule() {
        val dispatch = synchronized(gate) {
            if (!retiring || acquiring) return
            val cancel = !cancelPosted
            val destroy = !destroyPosted && !using
            val callback = terminal && !callbackPosted
            cancelPosted = true
            if (destroy) destroyPosted = true
            if (callback) callbackPosted = true
            booleanArrayOf(cancel, destroy, callback)
        }
        // All independent actions are dispatched before waiting for any actual return.
        if (dispatch[0]) queuedCancel.enqueue()
        if (dispatch[1]) queuedDestroy.enqueue()
        if (dispatch[2]) queuedCallback.enqueue()
    }
    /** Each wrapper exists before backend acquisition. Inline dispatch fails closed. */
    private inner class QueuedInvocation(private val action: Runnable, private val completion: CompletableFuture<Unit>) : Runnable {
        @Volatile private var postingThread: Thread? = null
        override fun run() {
            if (Thread.currentThread() === postingThread) completion.completeExceptionally(IllegalStateException("Inline biometric dispatch"))
            else action.run()
        }
        fun enqueue() {
            postingThread = Thread.currentThread()
            try { check(post(this)) { "Original biometric Main dispatch unavailable" } }
            catch (failure: Throwable) { completion.completeExceptionally(failure) }
            finally { postingThread = null }
        }
    }
    private val queuedCancel = QueuedInvocation(cancelInvocation, cancelDone)
    private val queuedDestroy = QueuedInvocation(destroyInvocation, hostDone)
    private val queuedCallback = QueuedInvocation(callbackBarrier, callbackDone)
    private val queuedDestroyBarrier = QueuedInvocation(destroyBarrier, hostDone)
    private fun finish() {
        val ready = synchronized(gate) { retiring && !acquiring && !using && hostReturned && callbackReturned && cancelReturned }
        if (ready) { synchronized(gate) { original = null }; bridgeDone.complete(Unit) }
    }
    val cancelled: Boolean get() = synchronized(gate) { cancelledState || hostDestroyed }
    val readyForAuthenticatedUse: Boolean get() = synchronized(gate) {
        terminal && callbackReturned && !retiring && !used && !hostDestroyed && expected != null
    }
    fun beginUse(): Any? = synchronized(gate) {
        if (!terminal || !callbackReturned || retiring || used || hostDestroyed || cancelledState) null
        else expected?.also { used = true; using = true }
    }
    fun endUse(completed: Boolean) {
        synchronized(gate) {
            using = false
            success = completed && !retiring && !cancelledState && !hostDestroyed
            expected = null
            retiring = true
        }
        original?.release()
        schedule()
        finish()
    }
    fun awaitAuthenticatedUse(timeout: Long, unit: TimeUnit): Boolean {
        readiness.await(timeout, unit)
        return readyForAuthenticatedUse
    }
    fun cancelSuccess() = synchronized(gate) {
        readiness.countDown()
        success = false
        cancelledState = true
        retiring = true
        if (!using) expected = null
    }
    fun claimSuccess(): Boolean = synchronized(gate) {
        if (!success || consumed || !hostReturned || !callbackReturned || !cancelReturned) false
        else { consumed = true; true }
    }
}
