package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

/** Synchronous recovery input/result migration only. The factory must not retain inputs
 * or start unjoined work; provider/store children and durable mutation admission remain
 * independent obligations. The branded result layout and final single-original rule
 * are unchanged. No recovered key escapes to a caller for a later authority.open(). */
internal fun PrimarySessionAuthority.authenticateRecovery(
    secret: CharArray,
    newPin: CharArray,
    factory: ((() -> Unit) -> Unit) -> ByteArray,
): Boolean {
    var attempt: PrimaryAuthAttempt? = null
    var handoff: OwnedAuthenticationKey? = null
    var accepted = false
    try {
        val current = beginAuthentication().also { attempt = it }
        var input: RecoveryPinInput? = null
        var owned: OwnedResource<RecoveryPinInput>? = null
        var failure: Throwable? = null
        try {
            owned = createAttemptOwned(current, OwnedResourceManifest.io("recovery-new-pin")) {
                attach("recovery-new-pin", RecoveryPinInput(newPin).also { input = it })
            }
            handoff = prepareAuthentication(current, secret) {
                val invokingThread = Thread.currentThread()
                var active = true
                var used = false
                val mutate: (() -> Unit) -> Unit = { action ->
                    check(Thread.currentThread() === invokingThread && active && !used) {
                        "Recovery mutation invocation unavailable"
                    }
                    used = true
                    // No Native call inherits this short Primary gate. This denies
                    // already-stale attempts; a prepared-command boundary is still
                    // required to resolve the remaining check-to-write race.
                    checkPreparation(current)
                    action()
                }
                try { factory(mutate) } finally { active = false }
            }
        } catch (problem: Throwable) { failure = problem; throw problem }
        finally {
            // Actual synchronous preparation has returned even if it never entered
            // the factory. Cancellation cannot wipe a still-borrowed new PIN.
            input?.returned()
            owned?.let { original ->
                original.close()
                try {
                    check(original.retirement.await(20, TimeUnit.SECONDS) && !original.releaseFailed) {
                        "Recovery PIN retirement incomplete"
                    }
                } catch (retirement: Throwable) {
                    if (retirement is InterruptedException) Thread.currentThread().interrupt()
                    if (failure != null) failure.addSuppressed(retirement) else throw retirement
                }
            }
        }
        // All non-result originals must have actually retired before this unchanged
        // exact-attempt authorization boundary. No outstanding-worker exemption.
        accepted = completeAuthentication(current, checkNotNull(handoff))
        return accepted
    } finally {
        handoff?.close()
        if (!accepted) attempt?.let { cancelAuthentication(it) }
        // The synchronous factory has returned on every path reaching this finally.
        secret.fill('\u0000'); newPin.fill('\u0000')
    }
}

/** One physical caller-supplied new-PIN buffer, independently charged before use. */
private class RecoveryPinInput(private val pin: CharArray) : AcknowledgedCloseable {
    private val gate = Any()
    private val done = CompletableFuture<Unit>()
    private var using = true
    private var retired = false
    fun returned() { synchronized(gate) { using = false }; finishIfReady() }
    override fun closeAcknowledged(): CompletionStage<Unit> {
        synchronized(gate) { retired = true }; finishIfReady(); return done
    }
    private fun finishIfReady() {
        val ready = synchronized(gate) {
            if (retired && !using) { pin.fill('\u0000'); true } else false
        }
        if (ready) done.complete(Unit)
    }
}
