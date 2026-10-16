package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** A single verified key awaiting short admission into its original authority.
 * No key getter, general resource transfer, or asynchronous consumer capability. */
class OwnedAuthenticationKey internal constructor(
    private val owned: OwnedResource<OriginalAuthenticationKey>,
    private val authority: Any,
    private val attempt: Any,
) : AutoCloseable {
    val retirement: RetirementAcknowledgement get() = owned.retirement
    internal val original: ReleaseReservation get() = owned.original
    internal fun belongsTo(authority: Any, attempt: Any) = this.authority === authority && this.attempt === attempt
    internal fun takeForAdmission(authority: Any, attempt: Any): ByteArray {
        check(this.authority === authority && this.attempt === attempt)
        return original.consumeAuthenticationHandoff(attempt, owned.value)
    }
    override fun close() = owned.close()
}

/** Only a synchronous leaf is supported: factory must not start unjoined work.
 * This original does not claim ownership of a caller's implicit provider/IO children. */
internal fun prepareAuthenticationKey(
    authority: Any, attempt: Any, pin: CharArray,
    create: (OwnedResourceManifest, OwnedFactoryScope.() -> OriginalAuthenticationKey) -> OwnedResource<OriginalAuthenticationKey>,
    checkCurrent: () -> Unit, factory: () -> ByteArray,
): OwnedAuthenticationKey {
    var entered = false
    try {
        val owned = create(OwnedResourceManifest.io("result", "invocation", "input")) {
            // Allocate inert metadata before attaching any child; even a partial
            // attachment failure must not invent a producer that never entered.
            val result = OriginalAuthenticationKey(AuthenticationKeyFactory)
            val invocation = AuthenticationInvocation()
            val input = AuthenticationPin(pin)
            try {
                attach("result", result)
                attach("invocation", invocation)
                attach("input", input)
                entered = true
                checkCurrent()
                result.bind(factory())
                checkCurrent()
            } finally {
                // The actual synchronous factory has unwound. No cancellation or
                // completed Future is used as evidence that the factory returned.
                result.finishProducer(); input.finishUse(); invocation.returned()
            }
            retireChildren("invocation", "input")
            result
        }
        return try { OwnedAuthenticationKey(owned, authority, attempt) }
        catch (failure: Throwable) { owned.close(); throw failure }
    } catch (failure: Throwable) {
        // Before admission the caller still owns the input. After attachment its
        // original must wait for the real borrower instead of wiping concurrently.
        if (!entered) pin.fill('\u0000')
        throw failure
    }
}

private object AuthenticationKeyFactory

internal class OriginalAuthenticationKey(private val factoryKind: Any? = null) : AcknowledgedCloseable {
    private val gate = Any()
    private val done = CompletableFuture<Unit>()
    private var key: ByteArray? = null
    private var producer = true
    private var retired = false
    internal fun bind(value: ByteArray) = synchronized(gate) {
        if (value.size != 32) { value.fill(0); error("Invalid authentication key") }
        check(producer && key == null)
        key = value
    }
    internal fun finishProducer() { synchronized(gate) { producer = false }; finishIfReady() }
    internal fun hasAuthenticationLayout(children: Int): Boolean =
        (children == 3 && factoryKind === AuthenticationKeyFactory) ||
            (children == 4 && factoryKind === ThreadAuthenticationKeyFactory)
    internal fun take(): ByteArray = synchronized(gate) {
        check(hasAuthenticationLayout(3) || hasAuthenticationLayout(4)) { "Wrong authentication factory original" }
        check(!retired && !producer)
        checkNotNull(key).also { key = null }
    }
    /** Bounded invalidation only; actual release return/acknowledgement remains separate. */
    internal fun invalidate() = synchronized(gate) {
        retired = true
        if (!producer) { key?.fill(0); key = null }
    }
    override fun closeAcknowledged(): CompletionStage<Unit> {
        invalidate(); finishIfReady(); return done
    }
    private fun finishIfReady() {
        val ready = synchronized(gate) {
            if (retired && !producer) { key?.fill(0); key = null; true } else false
        }
        if (ready) done.complete(Unit)
    }
}

private class AuthenticationPin(private val pin: CharArray) : AcknowledgedCloseable {
    private val gate = Any()
    private val done = CompletableFuture<Unit>()
    private var using = true
    private var retired = false
    fun finishUse() { synchronized(gate) { using = false }; finishIfReady() }
    override fun closeAcknowledged(): CompletionStage<Unit> {
        synchronized(gate) { retired = true }; finishIfReady(); return done
    }
    private fun finishIfReady() {
        val ready = synchronized(gate) { if (retired && !using) { pin.fill('\u0000'); true } else false }
        if (ready) done.complete(Unit)
    }
}

private class AuthenticationInvocation : AcknowledgedCloseable {
    private val done = CompletableFuture<Unit>()
    fun returned() { done.complete(Unit) }
    override fun closeAcknowledged(): CompletionStage<Unit> = done
}

/** Four fixed children: original key, synchronous invocation, borrowed input and actual Thread.
 * This does not own an enclosing Executor queue or the factory's implicit provider/file children. */
internal fun prepareThreadAuthenticationKey(
    authority: Any, attempt: Any, pin: CharArray, secret: ByteArray?,
    create: (OwnedResourceManifest, OwnedFactoryScope.() -> OriginalAuthenticationKey) -> OwnedResource<OriginalAuthenticationKey>,
    checkCurrent: () -> Unit, factory: () -> ByteArray,
    checkProduced: (ReleaseReservation) -> Unit = { checkCurrent() },
): OwnedAuthenticationKey {
    var entered = false
    try {
        val owned = create(OwnedResourceManifest.io("result", "invocation", "input", "worker")) {
            val result = OriginalAuthenticationKey(ThreadAuthenticationKeyFactory)
            val invocation = AuthenticationInvocation()
            val input = AuthenticationThreadInput(pin, secret)
            try {
                attach("result", result)
                attach("invocation", invocation)
                attach("input", input)
                entered = true
                checkCurrent()
                val worker = create("worker", { it.close() }) {
                    AuthenticationWorker {
                        checkCurrent()
                        result.bind(factory())
                        checkProduced(original)
                    }
                }.value
                // Real join, outside every ranked gate. The provider's finally marker,
                // a completed Future, and Executor.execute returning are not substitutes.
                worker.runAndJoin()
            } finally {
                result.finishProducer(); input.finishUse(); invocation.returned()
            }
            retireChildren("invocation", "input", "worker")
            result
        }
        return try { OwnedAuthenticationKey(owned, authority, attempt) }
        catch (failure: Throwable) { owned.close(); throw failure }
    } catch (failure: Throwable) {
        if (!entered) { pin.fill('\u0000'); secret?.fill(0) }
        throw failure
    }
}

private object ThreadAuthenticationKeyFactory

/** Cancellation before start seals the same inert Thread; no late-start resurrection. */
private class AuthenticationWorker(body: () -> Unit) : AutoCloseable {
    private val gate = Any()
    private var started = false
    private var closed = false
    @Volatile private var failure: Throwable? = null
    private val thread = Thread({ try { body() } catch (problem: Throwable) { failure = problem } }, "hidden-authentication")

    fun runAndJoin() {
        var startFailure: Throwable? = null
        synchronized(gate) {
            check(!closed && !started) { "Original authentication worker unavailable" }
            started = true
            try { thread.start() } catch (problem: Throwable) { startFailure = problem }
        }
        val interrupted = joinActual()
        startFailure?.let { throw it }
        failure?.let { throw it }
        if (interrupted) throw InterruptedException("Authentication coordinator interrupted")
    }
    override fun close() {
        val mustJoin = synchronized(gate) { closed = true; started }
        if (mustJoin) joinActual()
    }
    private fun joinActual(): Boolean {
        check(Thread.currentThread() !== thread) { "Authentication worker cannot acknowledge itself" }
        var interrupted = false
        while (thread.isAlive) {
            try { thread.join() } catch (_: InterruptedException) { interrupted = true }
        }
        if (interrupted) Thread.currentThread().interrupt()
        return interrupted
    }
}

private class AuthenticationThreadInput(private val pin: CharArray, private val secret: ByteArray?) : AcknowledgedCloseable {
    private val gate = Any()
    private val done = CompletableFuture<Unit>()
    private var using = true
    private var retired = false
    fun finishUse() { synchronized(gate) { using = false }; finishIfReady() }
    override fun closeAcknowledged(): CompletionStage<Unit> {
        synchronized(gate) { retired = true }; finishIfReady(); return done
    }
    private fun finishIfReady() {
        val ready = synchronized(gate) {
            if (retired && !using) { pin.fill('\u0000'); secret?.fill(0); true } else false
        }
        if (ready) done.complete(Unit)
    }
}
