package uk.co.traynor.privategallery.core.security

import android.content.SharedPreferences
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** One process-wide metadata guard for the existing Primary biometric preference slot.
 * No queue, worker, Native call or waiting: competing mutations are refused.
 * The opaque identity prevents old cleanup from releasing a replacement's admission. */
internal class PrimaryBiometricPreferenceRegistry {
    private val current = AtomicReference<Mutation?>()
    internal inner class Mutation internal constructor() : AutoCloseable {
        private val returned = AtomicBoolean()
        private val quarantined = AtomicBoolean()
        fun requireHeld() { check(!returned.get() && current.get() === this) { "Biometric preference mutation unavailable" } }
        fun quarantine() { requireHeld(); quarantined.set(true) }
        override fun close() {
            if (returned.compareAndSet(false, true) && !quarantined.get()) check(current.compareAndSet(this, null)) {
                "Biometric preference slot ownership lost"
            }
        }
    }
    fun claim(): Mutation = Mutation().also { check(current.compareAndSet(null, it)) { "Biometric preference mutation in progress" } }
}

internal object PrimaryBiometricPreferenceSlot {
    internal val process = PrimaryBiometricPreferenceRegistry()
    fun claim(): PrimaryBiometricPreferenceRegistry.Mutation = process.claim()
}

/** One exact encrypted preference command. The original exists before slot claim,
 * encoding or editor construction. Known no-write cleanup, or durable commit success
 * plus actual return, can retire the app command. Entered false/throw cannot acknowledge
 * queued provider work and leaves the original failed and charged. */
internal fun persistPrimaryBiometricPreferences(operation: PrimaryOperation, prepare: () -> SharedPreferences.Editor) {
    persistPrimaryBiometricPreferencesIn(operation, prepare, PrimaryBiometricPreferenceSlot.process)
}

/** The production entry is fixed above; this private implementation also permits
 * test-only synthetic-process reflection without resetting a failed production slot. */
private fun persistPrimaryBiometricPreferencesIn(operation: PrimaryOperation, prepare: () -> SharedPreferences.Editor,
    registry: PrimaryBiometricPreferenceRegistry) {
    operation.requireNativeOutsideAuthorityGate()
    operation.requireScope(PrimaryScope.CREDENTIALS)
    var original: ReleaseReservation? = null
    var command: OriginalBiometricPreferenceWrite? = null
    var failure: Throwable? = null
    try {
        operation.createOwned(OwnedResourceManifest.io("biometric-preference-write")) {
            original = this.original
            val write = attach("biometric-preference-write", OriginalBiometricPreferenceWrite(registry)).also { command = it }
            var writeFailure: Throwable? = null
            try {
                operation.requireScope(PrimaryScope.CREDENTIALS)
                write.claimSlot()
                write.bind(prepare())
                // This authorizes only the same already-prepared, single-use editor.
                // An admitted commit may finish after revoke; it grants no later admission.
                operation.commit {
                    check(!this.original.retiring && !this.original.failed)
                    write.admit()
                }
                operation.requireNativeOutsideAuthorityGate()
                check(write.commitExact()) { "Unable to save biometric vault envelope" }
            } catch (problem: Throwable) { writeFailure = problem; throw problem }
            finally {
                try { write.invocationReturned() }
                catch (cleanup: Throwable) { if (writeFailure != null) writeFailure.addSuppressed(cleanup) else throw cleanup }
            }
            write
        }
    } catch (problem: Throwable) {
        failure = problem
        throw problem
    } finally {
        original?.let { owner ->
            owner.release()
            if (command?.providerCompletionUncertain == true) {
                // A failed public commit has no supported original disk-work ACK.
                // Its exceptional receipt keeps the original charged. Do not wait
                // for a success marker that cannot honestly arrive, or mask the
                // original persistence failure with a fabricated cleanup success.
                check(failure != null) { "Ambiguous preference provider result" }
            } else try {
                check(owner.terminalRetirement.await(20, TimeUnit.SECONDS) && !owner.failed) {
                    "Biometric preference write retirement incomplete"
                }
            } catch (retirement: Throwable) {
                if (retirement is InterruptedException) Thread.currentThread().interrupt()
                if (failure != null) failure.addSuppressed(retirement) else throw retirement
            }
        }
    }
    // Separate final authorization after every mandatory cleanup/actual return above.
    operation.commit { }
}

private class OriginalBiometricPreferenceWrite(private val registry: PrimaryBiometricPreferenceRegistry) : AcknowledgedCloseable {
    private val done = CompletableFuture<Unit>()
    private var slot: PrimaryBiometricPreferenceRegistry.Mutation? = null
    private var editor: SharedPreferences.Editor? = null
    private var admitted = false
    private var executed = false
    private var providerFailure: Throwable? = null
    val providerCompletionUncertain: Boolean get() = providerFailure != null
    fun claimSlot() { check(slot == null); slot = registry.claim() }
    fun bind(value: SharedPreferences.Editor) { check(slot != null && editor == null && !admitted); editor = value }
    fun admit() { check(slot != null && editor != null && !admitted); admitted = true }
    fun commitExact(): Boolean {
        check(admitted && !executed); executed = true
        return try {
            checkNotNull(editor).commit().also { committed ->
                if (!committed) markProviderUncertain(IllegalStateException("Biometric preference provider completion unavailable"))
            }
        } catch (problem: Throwable) {
            if (providerFailure == null) markProviderUncertain(problem)
            throw problem
        }
    }
    private fun markProviderUncertain(problem: Throwable) {
        providerFailure = problem
        checkNotNull(slot).quarantine()
    }
    fun invocationReturned() {
        // Both synchronous Native callbacks have unwound; no editor is selected or retried here.
        editor = null
        try {
            slot?.close(); slot = null
            val failure = providerFailure
            if (failure == null) done.complete(Unit) else done.completeExceptionally(failure)
        } catch (cleanup: Throwable) {
            done.completeExceptionally(cleanup)
            throw cleanup
        }
    }
    override fun closeAcknowledged(): CompletionStage<Unit> = done
}

/** Existing recovery still has broader provider/transaction obligations. This guard
 * seals its exact synchronous callback before PIN mutation, including late, repeated
 * and foreign-thread invocations; only one exact disable can finish the scope. */
internal fun withPrimaryBiometricRecoveryDisable(disableExact: () -> Boolean, action: (() -> Unit, () -> Unit) -> Unit) {
    val mutation = PrimaryBiometricPreferenceSlot.claim()
    val invocationThread = Thread.currentThread()
    var entered = false
    var invoked = false
    var completed = false
    val enter = {
        check(Thread.currentThread() === invocationThread) { "Recovery mutation belongs to another worker" }
        mutation.requireHeld()
        check(!entered) { "Recovery mutation already entered" }; entered = true
    }
    val disable = {
        check(Thread.currentThread() === invocationThread) { "Recovery mutation belongs to another worker" }
        mutation.requireHeld()
        check(entered && !invoked) { "Biometric disable command unavailable" }; invoked = true
        check(disableExact()) { "Unable to disable biometric vault envelope" }
        completed = true
    }
    try {
        action(enter, disable)
        check(completed) { "Recovery mutation did not complete" }
    } finally { mutation.close() }
}
