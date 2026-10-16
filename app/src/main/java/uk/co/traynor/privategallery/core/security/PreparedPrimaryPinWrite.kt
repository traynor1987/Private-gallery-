package uk.co.traynor.privategallery.core.security

import android.content.SharedPreferences
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** Fixed encrypted legacy PIN fields only. Crypto/encoding is deliberately upstream. */
internal class PrimaryPinRecord(val salt: String, val nonce: String, val ciphertext: String) {
    init { require(salt.length == 24 && nonce.length == 16 && ciphertext.length == 64) { "Invalid PIN record shape" } }
    private val fields = mapOf("salt" to salt, "nonce" to nonce, "ciphertext" to ciphertext)
    internal fun matches(values: Map<String, *>): Boolean = fields == values
    internal fun prepare(editor: SharedPreferences.Editor): SharedPreferences.Editor =
        editor.putString("salt", salt).putString("nonce", nonce).putString("ciphertext", ciphertext)
}

/** Exact preowned PIN write prerequisite. No production caller is switched in batch A.
 * Attach child 0 before claim/preparation. A restore also attaches child 1 before either
 * Editor is acquired. No KDF/provider/input/result ownership is implied by this command. */
internal class PreparedPrimaryPinWrite private constructor(
    operation: PrimaryOperation,
    original: ReleaseReservation,
    private val purpose: PrimaryCredentialPurpose,
    private val registry: PrimaryCredentialMutationRegistry,
) : AcknowledgedCloseable {
    companion object {
        fun create(operation: PrimaryOperation, original: ReleaseReservation, purpose: PrimaryCredentialPurpose) =
            PreparedPrimaryPinWrite(operation, original, purpose, PrimaryCredentialMutationSlot.process)
    }
    private var operation: PrimaryOperation? = operation
    private var original: ReleaseReservation? = original
    private var thread: Thread? = Thread.currentThread()
    private val done = CompletableFuture<Unit>()
    private var mutation: PrimaryCredentialMutationRegistry.Mutation? = null
    private var preferences: SharedPreferences? = null
    private var editor: SharedPreferences.Editor? = null
    private var rollback: PreparedPrimaryPinRollback? = null
    private var prepared = false
    private var admitted = false
    private var executed = false
    private var succeeded = false
    private var returned = false
    private var committing = false
    private var providerFailure: Throwable? = null
    init {
        operation.requireNativeOutsideAuthorityGate()
        check(!Thread.holdsLock(PrimaryVaultSetupGuard.storageLock)) { "PIN preparation under storage gate" }
        require(purpose in setOf(PrimaryCredentialPurpose.SETUP, PrimaryCredentialPurpose.CHANGE_PIN,
            PrimaryCredentialPurpose.RECOVERY, PrimaryCredentialPurpose.RESTORE))
    }
    private fun requireThread() { check(!returned && thread === Thread.currentThread()) { "Original PIN invocation unavailable" } }
    private fun outside() {
        checkNotNull(operation).requireNativeOutsideAuthorityGate()
        check(!Thread.holdsLock(PrimaryVaultSetupGuard.storageLock)) { "PIN command under storage gate" }
        preferences?.let { check(!Thread.holdsLock(it)) { "PIN command under preference gate" } }
    }
    internal fun originalFor(owner: PrimaryOperation): ReleaseReservation {
        requireThread(); check(operation === owner) { "Foreign credential operation" }
        return checkNotNull(original)
    }
    fun attachRollback(value: PreparedPrimaryPinRollback) {
        requireThread(); check(purpose == PrimaryCredentialPurpose.RESTORE && rollback == null && mutation == null)
        value.requireBinding(checkNotNull(operation), checkNotNull(original)); rollback = value
        // Retain this exact attached child for known-unentered cleanup before the
        // live-use check can reject a revoke that raced attachment and linkage.
        checkNotNull(original).requireNativeUse(1, value)
    }
    fun claimSlot() {
        requireThread(); outside(); check(mutation == null)
        checkNotNull(operation).checkPreparedPin(this)
        check(purpose != PrimaryCredentialPurpose.RESTORE || rollback != null) { "Restore rollback child required" }
        mutation = registry.claim(checkNotNull(original), this, purpose)
        rollback?.bindMutation(checkNotNull(mutation))
    }
    fun prepare(preferences: SharedPreferences, predecessor: PrimaryPinRecord?, replacement: PrimaryPinRecord) {
        requireThread(); outside(); check(!prepared && editor == null && !admitted)
        checkNotNull(mutation).requireHeld(); checkNotNull(operation).checkPreparedPin(this)
        if (purpose == PrimaryCredentialPurpose.RESTORE) check(predecessor == null) { "Restore requires empty PIN predecessor" }
        this.preferences = preferences
        outside()
        prepared = true
        val snapshot = preferences.all
        check(if (predecessor == null) snapshot.isEmpty() else predecessor.matches(snapshot)) { "PIN predecessor changed" }
        editor = replacement.prepare(preferences.edit())
        rollback?.prepare(preferences, replacement)
    }
    fun admit() { requireThread(); outside(); checkNotNull(operation).admitPreparedPin(this) }
    /** Called only by the typed authority method, under its short original-epoch gate. */
    internal fun admitMetadata() {
        requireThread(); checkNotNull(operation).requirePreparedPinAdmissionGate(this)
        checkNotNull(mutation).requireHeld()
        check(prepared && editor != null && !admitted && !executed)
        rollback?.requirePrepared()
        admitted = true
    }
    fun commitExact() {
        requireThread(); outside(); check(admitted && !executed)
        checkNotNull(mutation).requireHeld(); executed = true; committing = true
        try {
            check(checkNotNull(editor).commit()) { "Unable to persist PIN envelope; outcome unavailable" }
            succeeded = true
        } catch (failure: Throwable) {
            providerFailure = failure
            checkNotNull(mutation).markAmbiguous(); throw failure
        } finally { committing = false; rollback?.commitReturned(succeeded) }
    }
    /** Explicit durable decision, distinct from later session/UI acceptance. */
    fun finishInstalled() {
        if (purpose == PrimaryCredentialPurpose.RESTORE) checkNotNull(rollback).finishInstalled()
        else { requireThread(); outside(); check(succeeded); checkNotNull(mutation).decideFinished() }
    }
    /** Only the separately prefunded, exact installed-record cleanup command. */
    fun rollbackExact() { check(purpose == PrimaryCredentialPurpose.RESTORE); checkNotNull(rollback).rollbackExact() }
    fun invocationReturned() {
        if (returned) return
        requireThread(); outside(); check(!committing) { "PIN provider invocation has not returned" }
        // A command that never executed cannot have installed anything. An ambiguous
        // ordinary write remains quarantined. SharedPreferences false/throw can leave
        // queued disk work without a supported public completion receipt: app-call
        // return and reference clearing cannot positively retire that original.
        if (!executed) {
            if (rollback != null) rollback!!.abandonUninstalled() else mutation?.decideFinished()
        } else if (!succeeded && rollback == null) mutation?.decideFinished()
        editor = null; preferences = null; operation = null; original = null; thread = null
        returned = true
        val failure = providerFailure; providerFailure = null
        if (failure == null) done.complete(Unit) else done.completeExceptionally(failure)
    }
    override fun closeAcknowledged(): CompletionStage<Unit> = done
}

/** Independent restore child. It survives write return until an explicit durable
 * success or same-claim rollback. A failed cleanup never positively retires. */
internal class PreparedPrimaryPinRollback(operation: PrimaryOperation, original: ReleaseReservation) : AcknowledgedCloseable {
    private var operation: PrimaryOperation? = operation
    private var original: ReleaseReservation? = original
    private var thread: Thread? = Thread.currentThread()
    private var mutation: PrimaryCredentialMutationRegistry.Mutation? = null
    private var preferences: SharedPreferences? = null
    private var installed: PrimaryPinRecord? = null
    private var editor: SharedPreferences.Editor? = null
    private var commitReturned = false
    private var installedSuccessfully = false
    private var decided = false
    private var executing = false
    private val done = CompletableFuture<Unit>()
    internal fun requireBinding(owner: PrimaryOperation, original: ReleaseReservation) {
        check(this.operation === owner && this.original === original)
        original.requirePreparedPinRollbackCleanupLink(owner, this)
    }
    private fun outside() {
        check(thread === Thread.currentThread() && !decided && !executing) { "Original restore decision unavailable" }
        checkNotNull(operation).requireNativeOutsideAuthorityGate()
        check(!Thread.holdsLock(PrimaryVaultSetupGuard.storageLock)) { "PIN rollback under storage gate" }
        preferences?.let { check(!Thread.holdsLock(it)) { "PIN rollback under preference gate" } }
    }
    internal fun bindMutation(value: PrimaryCredentialMutationRegistry.Mutation) {
        outside(); check(mutation == null); check(value.purpose == PrimaryCredentialPurpose.RESTORE); mutation = value
    }
    internal fun prepare(preferences: SharedPreferences, record: PrimaryPinRecord) {
        outside(); check(editor == null && installed == null); checkNotNull(original).requireNativeUse(1, this)
        this.preferences = preferences; installed = record; outside()
        editor = preferences.edit().clear()
    }
    internal fun requirePrepared() { check(!decided && editor != null && installed != null && mutation != null) }
    internal fun commitReturned(success: Boolean) { check(!commitReturned); commitReturned = true; installedSuccessfully = success }
    internal fun finishInstalled() {
        outside(); check(commitReturned && installedSuccessfully)
        checkNotNull(mutation).decideFinished(); finish(null)
    }
    internal fun abandonUninstalled() {
        outside(); check(!commitReturned); mutation?.decideFinished(); finish(null)
    }
    internal fun rollbackExact() {
        outside(); requirePrepared(); check(commitReturned)
        val mutation = checkNotNull(mutation)
        mutation.requireHeld(); executing = true
        try {
            val snapshot = checkNotNull(preferences).all
            if (!snapshot.isEmpty()) {
                check(checkNotNull(installed).matches(snapshot)) { "Restored PIN ownership changed" }
                check(checkNotNull(editor).commit()) { "Unable to roll back owned PIN envelope" }
            }
            // This records only the exact rollback outcome. It never acknowledges
            // an earlier ambiguous provider invocation or clears its quarantine.
            mutation.decideRolledBack(); finish(null)
        } catch (failure: Throwable) {
            mutation.markAmbiguous(); mutation.decideFinished(); finish(failure); throw failure
        }
    }
    private fun finish(failure: Throwable?) {
        editor = null; preferences = null; installed = null; operation = null; original = null; thread = null
        decided = true; executing = false
        if (failure == null) done.complete(Unit) else done.completeExceptionally(failure)
    }
    override fun closeAcknowledged(): CompletionStage<Unit> = done
}
