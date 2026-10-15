package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** An admitted session source, independent of the producer lease's normal closure.
 * Trusted synchronous consumers must not retain the array or start unjoined work.
 * This does not provide a lifetime borrow for players or asynchronous Native users. */
internal class OwnedPlaintext internal constructor(
    private val owned: OwnedResource<OriginalPlaintext>,
) : AutoCloseable {
    val retirement: RetirementAcknowledgement get() = owned.retirement
    val releaseFailed: Boolean get() = owned.releaseFailed
    fun useBytes(consume: (ByteArray) -> Unit) = owned.value.useBytes(consume)
    override fun close() = owned.close()
}

/** Funds the presentation cell before decrypt/copy begins. The factory must return
 * a fresh exclusive array; its independent IO/intermediate resources need their own
 * manifests. No backing array or ambient ownership lookup escapes this facade. */
internal fun PrimaryOperation.createOwnedPlaintext(factory: () -> ByteArray): OwnedPlaintext {
    requireNativeOutsideAuthorityGate()
    requireScope(PrimaryScope.READ)
    val owner = this
    val owned = createSessionOwned(OwnedResourceManifest.presentation("plaintext")) {
        owner.requirePresentationOriginal(original)
        val cell = attach("plaintext", OriginalPlaintext(owner, original))
        try {
            owner.requireScope(PrimaryScope.READ)
            cell.bind(factory())
            owner.requireScope(PrimaryScope.READ)
            cell
        } finally { cell.finishFactory() }
    }
    try { return OwnedPlaintext(owned) }
    catch (failure: Throwable) { owned.close(); throw failure }
}

/** Attached before construction: revoke can deny access immediately while actual
 * wipe/acknowledgement waits for construction and every admitted synchronous use. */
internal class OriginalPlaintext internal constructor(
    private val owner: PrimaryOperation,
    private val original: ReleaseReservation,
) : AcknowledgedCloseable {
    private val gate = Any()
    private val acknowledged = CompletableFuture<Unit>()
    private var factoryPending = true
    private var actual: ByteArray? = null
    private var borrowed = false
    private var retired = false

    internal fun bind(value: ByteArray) = synchronized(gate) {
        check(factoryPending && actual == null) { "Original plaintext already bound" }
        actual = value
    }

    internal fun finishFactory() {
        synchronized(gate) { factoryPending = false }
        completeIfReady()
    }

    private fun checkSession() {
        owner.requireNativeOutsideAuthorityGate()
        owner.requirePresentationOriginal(original)
        original.requireNativeUse(0, this)
        // Closing the read producer must not invalidate its already admitted
        // session source. This check grants no new operation/IO authority.
        check(owner.isCurrent && !original.retiring) { "Original plaintext unavailable" }
    }

    internal fun useBytes(consume: (ByteArray) -> Unit) {
        checkSession()
        val value = synchronized(gate) {
            check(!retired && !original.retiring && !factoryPending && !borrowed) { "Original plaintext unavailable" }
            checkNotNull(actual).also { borrowed = true }
        }
        try {
            checkSession()
            consume(value)
            checkSession()
        } finally {
            synchronized(gate) { borrowed = false }
            completeIfReady()
        }
    }

    override fun closeAcknowledged(): CompletionStage<Unit> {
        synchronized(gate) { retired = true }
        completeIfReady()
        return acknowledged
    }

    private fun completeIfReady() {
        val complete = synchronized(gate) {
            // Original retirement is visible before its funded callback runs.
            // A late constructor/returning borrow can perform its own safe wipe.
            if (original.retiring) retired = true
            if (retired && !factoryPending && !borrowed) {
                actual?.fill(0)
                actual = null
                true
            } else false
        }
        // Completion can invoke accounting callbacks; never run under this gate.
        if (complete) acknowledged.complete(Unit)
    }
}
