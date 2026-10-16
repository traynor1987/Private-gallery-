package uk.co.traynor.privategallery.core.security

import java.util.concurrent.TimeUnit

/** Synchronous coordinator adapter. The actual Native worker is acquired only after its
 * original physical child is reserved; cancellation joins that same Thread. */
internal fun runOwnedBiometricWork(owner: BiometricOriginalOwner, action: () -> Unit) {
    val owned = owner.create(OwnedResourceManifest.io("biometric-worker")) {
        create("biometric-worker", { it.close() }) { BiometricWorker(action) }
    }
    try { owned.value.value.runAndJoin() }
    finally {
        owned.close()
        check(owned.retirement.await(20, TimeUnit.SECONDS) && !owned.releaseFailed) { "Biometric worker retirement incomplete" }
    }
}

private class BiometricWorker(action: () -> Unit) : AutoCloseable {
    private val gate = Any()
    private var closed = false
    private var started = false
    @Volatile private var failure: Throwable? = null
    private val thread = Thread({ try { action() } catch (problem: Throwable) { failure = problem } }, "biometric-native")
    fun runAndJoin() {
        synchronized(gate) { check(!closed && !started); started = true; thread.start() }
        val interrupted = joinActual()
        if (interrupted) throw InterruptedException()
        failure?.let { throw it }
    }
    override fun close() {
        val join = synchronized(gate) { closed = true; started }
        if (join) joinActual()
    }
    private fun joinActual(): Boolean {
        check(Thread.currentThread() !== thread)
        var interrupted = false
        while (thread.isAlive) try { thread.join() } catch (_: InterruptedException) { interrupted = true }
        if (interrupted) Thread.currentThread().interrupt()
        return interrupted
    }
}
