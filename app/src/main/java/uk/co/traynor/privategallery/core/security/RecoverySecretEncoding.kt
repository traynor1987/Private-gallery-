package uk.co.traynor.privategallery.core.security

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetEncoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** Recovery's first, synchronous preparation phase. No authentication result or raw
 * secret getter is exposed. The later fixed recovery producer must bind this exact
 * original to its own attempt and fund unwrap/wrapping before consuming the bytes.
 * This prerequisite is deliberately not wired into the legacy persistence callback. */
internal class RecoverySecretEncoding private constructor(private val encoder: () -> CharsetEncoder) {
    companion object {
        fun standard() = RecoverySecretEncoding { StandardCharsets.UTF_8.newEncoder() }
    }

    fun prepare(authority: PrimarySessionAuthority, attempt: PrimaryAuthAttempt,
        secret: CharArray): OwnedResource<PreparedRecoverySecret> {
        var entered = false
        try {
            // Reject inherited ranked gates before allocating tickets or beginning
            // any provider construction or mandatory retirement wait.
            authority.requireNativeOutsideGate()
            check(!Thread.holdsLock(PrimaryVaultSetupGuard.storageLock)) { "Recovery encoding under storage gate" }
            return authority.createAttemptOwned(attempt,
                OwnedResourceManifest.io("encoded-secret", "invocation", "input", "encoder", "scratch")) {
                val output = PreparedRecoverySecret()
                val invocation = PinCall()
                val input = RecoveryEncodingInput(secret)
                val provider = RecoveryEncoderCall()
                val scratch = RecoveryEncodingScratch()
                try {
                    attach("encoded-secret", output); attach("invocation", invocation)
                    attach("input", input); attach("encoder", provider); attach("scratch", scratch)
                    entered = true
                    authority.checkPreparation(attempt)
                    original.requireNativeUse(0, output); original.requireNativeUse(2, input)
                    // Complete manifest, including the exact output, is funded before
                    // normalization, scratch allocation, views or CharsetEncoder.
                    provider.bind(encoder())
                    authority.checkPreparation(attempt)
                    input.encode(provider, scratch, output)
                    authority.checkPreparation(attempt)
                } finally {
                    // Actual encode/flush/copy has returned or thrown. Revoke cannot
                    // erase an input/output/scratch still used by that invocation.
                    provider.returned(); scratch.returned(); input.returned()
                    output.returned(); invocation.returned()
                }
                retireChildren("invocation", "input", "encoder", "scratch")
                authority.checkPreparation(attempt)
                original.requireNativeUse(0, output)
                output
            }
        } catch (failure: Throwable) {
            if (failure is InterruptedException) Thread.currentThread().interrupt()
            if (!entered) secret.fill('\u0000')
            throw failure
        }
    }
}

/** Exact encoded KDF input is independently funded from the encoder's scratch.
 * Only the upcoming fixed recovery producer may acquire a purpose-specific borrow;
 * this type intentionally has no raw accessor or arbitrary consumer entry point. */
internal class PreparedRecoverySecret : AcknowledgedCloseable {
    private val gate = Any()
    private val done = CompletableFuture<Unit>()
    private var bytes: ByteArray? = null
    private var using = true
    private var retired = false
    internal fun bind(value: ByteArray) = synchronized(gate) { check(using && bytes == null); bytes = value }
    internal fun returned() { synchronized(gate) { using = false }; finish() }
    override fun closeAcknowledged(): CompletionStage<Unit> {
        synchronized(gate) { retired = true }; finish(); return done
    }
    private fun finish() {
        val ready = synchronized(gate) {
            if (retired && !using) { bytes?.fill(0); bytes = null; true } else false
        }
        if (ready) done.complete(Unit)
    }
}

private class RecoveryEncodingInput(private val secret: CharArray) : AcknowledgedCloseable {
    private val gate = Any()
    private val done = CompletableFuture<Unit>()
    private var using = true
    private var retired = false
    fun encode(provider: RecoveryEncoderCall, scratch: RecoveryEncodingScratch, output: PreparedRecoverySecret) {
        synchronized(gate) { check(using && !retired) { "Original recovery input unavailable" } }
        // Compact before UTF8: whitespace between surrogate halves can join a pair.
        // Same Kotlin predicate as RecoveryKey.normalise, without an immutable String.
        var size = 0
        for (read in secret.indices) if (!secret[read].isWhitespace()) secret[size++] = secret[read]
        secret.fill('\u0000', size, secret.size)
        provider.encode(secret, size, scratch, output)
    }
    fun returned() { synchronized(gate) { using = false }; finish() }
    override fun closeAcknowledged(): CompletionStage<Unit> {
        synchronized(gate) { retired = true }; finish(); return done
    }
    private fun finish() {
        val ready = synchronized(gate) { if (retired && !using) { secret.fill('\u0000'); true } else false }
        if (ready) done.complete(Unit)
    }
}

private class RecoveryEncodingScratch : AcknowledgedCloseable {
    private val gate = Any()
    private val done = CompletableFuture<Unit>()
    private var bytes: ByteArray? = null
    private var using = true
    private var retired = false
    fun allocate(size: Int): ByteArray = synchronized(gate) {
        check(using && bytes == null)
        ByteArray(size).also { bytes = it }
    }
    fun returned() { synchronized(gate) { using = false }; finish() }
    override fun closeAcknowledged(): CompletionStage<Unit> {
        synchronized(gate) { retired = true }; finish(); return done
    }
    private fun finish() {
        val ready = synchronized(gate) { if (retired && !using) { bytes?.fill(0); bytes = null; true } else false }
        if (ready) done.complete(Unit)
    }
}

/** One synchronous standard encoder facade and bounded passive buffer views.
 * Native/private JVM copies are not claimed positively erased by dropping refs. */
private class RecoveryEncoderCall : AcknowledgedCloseable {
    private val done = CompletableFuture<Unit>()
    private var encoder: CharsetEncoder? = null
    private var chars: CharBuffer? = null
    private var bytes: ByteBuffer? = null
    fun bind(value: CharsetEncoder) {
        check(encoder == null); encoder = value
        value.onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE)
    }
    fun encode(secret: CharArray, length: Int, scratch: RecoveryEncodingScratch, output: PreparedRecoverySecret) {
        val selected = checkNotNull(encoder)
        // Widened size arithmetic is checked before allocation. Standard UTF8 has a maximum
        // of three bytes per UTF16 code unit, including malformed-input replacement.
        val maximum = kotlin.math.ceil(length.toDouble() * selected.maxBytesPerChar().toDouble())
        require(maximum.isFinite() && maximum <= Int.MAX_VALUE.toDouble()) { "Recovery UTF8 input too large" }
        val backing = scratch.allocate(maximum.toInt())
        chars = CharBuffer.wrap(secret, 0, length)
        bytes = ByteBuffer.wrap(backing)
        selected.encode(checkNotNull(chars), checkNotNull(bytes), true).throwExceptionIfErrorOrOverflow()
        selected.flush(checkNotNull(bytes)).throwExceptionIfErrorOrOverflow()
        // copyOf's exact result is attached before any next throwing/blocking call.
        output.bind(backing.copyOf(checkNotNull(bytes).position()))
    }
    fun returned() { chars = null; bytes = null; encoder = null; done.complete(Unit) }
    override fun closeAcknowledged(): CompletionStage<Unit> = done
    private fun java.nio.charset.CoderResult.throwExceptionIfErrorOrOverflow() {
        if (isError) throwException()
        check(isUnderflow) { "Recovery UTF8 scratch overflow" }
    }
}
