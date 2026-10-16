package uk.co.traynor.privategallery.core.security

import android.content.SharedPreferences
import android.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.bouncycastle.crypto.generators.SCrypt
import uk.co.traynor.privategallery.core.crypto.InvalidPinException

/** Fixed Primary encrypted source. Production construction pins the standard primitives;
 * the private constructor permits isolated host fixtures, never a caller's key producer. */
internal class PrimaryPinUnlockSource private constructor(
    private val preferences: SharedPreferences,
    private val decode: (String) -> ByteArray,
    private val derive: (ByteArray, ByteArray) -> ByteArray,
    private val cipher: () -> Cipher,
) {
    companion object {
        fun primaryPreferences(preferences: SharedPreferences) = PrimaryPinUnlockSource(preferences,
            { Base64.decode(it, Base64.NO_WRAP) }, { pin, salt -> SCrypt.generate(pin, salt, 32768, 8, 1, 32) },
            { Cipher.getInstance("AES/GCM/NoPadding") })
    }

    internal fun requireOutside() {
        check(!Thread.holdsLock(preferences)) { "PIN read under preference monitor" }
        check(!Thread.holdsLock(PrimaryVaultSetupGuard.storageLock)) { "PIN read under storage gate" }
    }

    internal fun producePhase(scope: OwnedFactoryScope, context: PrimaryPinProducer): PinCall = with(scope) {
        context.check()
        requireOutside()
        val root = PinCall()
        val salt = PinBytes(); val nonce = PinBytes(); val ciphertext = PinBytes()
        val pin = PinBytes(); val wrapping = PinBytes()
        val kdf = PinCall(); val provider = PinCipherCall()
        try {
            attach("pin-unwrap", root); attach("salt", salt); attach("nonce", nonce)
            attach("ciphertext", ciphertext); attach("pin-bytes", pin); attach("wrapping-key", wrapping)
            attach("kdf", kdf); attach("cipher", provider)
            // All eleven physical obligations exist before any payload/provider.
            context.allocateDestination()
            context.check(); val snapshot = preferences.all
            // Validate all public record shapes before decoder allocation. Android's
            // decoder sizes temporary storage from input length, including SKIP bytes.
            val encodedSalt = boundedRecord(snapshot["salt"], 16)
            val encodedNonce = boundedRecord(snapshot["nonce"], 12)
            val encodedCiphertext = boundedRecord(snapshot["ciphertext"], 48)
            context.check(); salt.bind(decode(encodedSalt)); salt.requireSize(16)
            context.check(); nonce.bind(decode(encodedNonce)); nonce.requireSize(12)
            context.check(); ciphertext.bind(decode(encodedCiphertext)); ciphertext.requireSize(48)
            context.encodePinInto(pin)
            context.check(); pin.deriveInto(salt, wrapping, this@PrimaryPinUnlockSource)
            wrapping.requireSize(32)
            context.check(); acquireCipherInto(provider)
            wrapping.initCipher(context, provider, nonce)
            ciphertext.decryptInto(context, provider)
        } finally {
            // Every synchronous read/KDF/Cipher call has unwound. Clear the facade
            // before its independently owned backing arrays can retire.
            provider.returned(); kdf.returned()
            wrapping.returned(); pin.returned(); ciphertext.returned(); nonce.returned(); salt.returned()
            root.returned()
        }
        root
    }

    /** Same standard Android Base64 SKIP/padding semantics, bounded public metadata.
     * UTF-8 non-ASCII bytes (and replacement '?' for unpaired surrogates) are SKIP.
     * Keep optional padding; the platform decoder still interprets every data bit. */
    private fun boundedRecord(value: Any?, bytes: Int): String {
        val text = checkNotNull(value as? String) { "Vault is not configured" }
        val paddedLength = ((bytes + 2) / 3) * 4
        val dataLength = (bytes * 8 + 5) / 6
        val bounded = StringBuilder(paddedLength)
        for (char in text) {
            if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char == '+' || char == '/' || char == '=') {
                check(bounded.length < paddedLength) { "Invalid PIN envelope size" }
                bounded.append(char)
            }
        }
        check(bounded.length == dataLength || bounded.length == paddedLength) { "Invalid PIN envelope size" }
        for (index in 0 until bounded.length) {
            check((bounded[index] == '=') == (index >= dataLength)) { "Invalid PIN envelope padding" }
        }
        return bounded.toString()
    }

    internal fun deriveExact(pin: ByteArray, salt: ByteArray): ByteArray = derive(pin, salt)
    private fun acquireCipherInto(provider: PinCipherCall) { provider.bind(cipher()) }

}

/** Original attempt, result and input are fixed; no key getter or arbitrary action. */
internal class PrimaryPinProducer(
    private val authority: PrimarySessionAuthority,
    private val attempt: PrimaryAuthAttempt,
    private val original: ReleaseReservation,
    private val result: OriginalAuthenticationKey,
    private var input: AuthenticationPin?,
) {
    private var thread: Thread? = Thread.currentThread()
    private var entered = false
    fun produce(source: PrimaryPinUnlockSource) {
        check(!entered); entered = true
        var phase: ReleaseReservation? = null
        var failure: Throwable? = null
        try {
            check()
            authority.createAttemptOwned(attempt, OwnedResourceManifest.io("pin-unwrap", "salt", "nonce", "ciphertext",
                "pin-bytes", "wrapping-key", "kdf", "cipher")) {
                phase = this.original
                source.producePhase(this, this@PrimaryPinProducer)
            }
        } catch (problem: Throwable) { failure = problem; throw problem }
        finally {
            try {
                phase?.let { owner ->
                    owner.release()
                    try {
                        check(owner.terminalRetirement.await(20, TimeUnit.SECONDS) && !owner.failed) { "PIN preparation retirement incomplete" }
                    } catch (cleanup: Throwable) {
                        if (cleanup is InterruptedException) Thread.currentThread().interrupt()
                        if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
                    }
                }
                if (failure == null) check()
            } finally { input = null; thread = null }
        }
    }
    internal fun check() {
        check(thread === Thread.currentThread() && input != null) { "Original PIN producer unavailable" }
        authority.requireNativeOutsideGate()
        check(!Thread.holdsLock(PrimaryVaultSetupGuard.storageLock)) { "PIN producer under storage gate" }
        authority.checkPinProducer(attempt, original, result, checkNotNull(input))
    }
    internal fun allocateDestination() { check(); result.allocatePrimaryPinDestination() }
    internal fun encodePinInto(destination: PinBytes) {
        check(); checkNotNull(input).encodePrimaryPinInto(destination)
    }
    internal fun decrypt(provider: PinCipherCall, ciphertext: ByteArray) {
        check(); result.decryptPrimaryPin(provider, ciphertext)
    }
}

/** Metadata exists before the exact payload; retirement waits for the whole synchronous
 * phase so revoke cannot wipe a key/input while a provider still reads or writes it. */
internal class PinBytes : AcknowledgedCloseable {
    private val gate = Any()
    private val done = CompletableFuture<Unit>()
    private var value: ByteArray? = null
    private var using = true
    private var retired = false
    fun bind(bytes: ByteArray) = synchronized(gate) { check(using && value == null); value = bytes }
    private fun bytes(): ByteArray = synchronized(gate) { check(using); checkNotNull(value) }
    fun requireSize(size: Int) { check(bytes().size == size) { "Invalid PIN envelope or wrapping key size" } }
    fun deriveInto(salt: PinBytes, output: PinBytes, source: PrimaryPinUnlockSource) {
        output.bind(source.deriveExact(bytes(), salt.bytes()))
    }
    fun initCipher(context: PrimaryPinProducer, provider: PinCipherCall, nonce: PinBytes) {
        provider.init(context, bytes(), nonce.bytes())
    }
    fun decryptInto(context: PrimaryPinProducer, provider: PinCipherCall) { context.decrypt(provider, bytes()) }
    fun returned() { synchronized(gate) { using = false }; finish() }
    override fun closeAcknowledged(): CompletionStage<Unit> {
        synchronized(gate) { retired = true }; finish(); return done
    }
    private fun finish() {
        val complete = synchronized(gate) {
            if (retired && !using) { value?.fill(0); value = null; true } else false
        }
        if (complete) done.complete(Unit)
    }
}

internal class PinCall : AcknowledgedCloseable {
    private val done = CompletableFuture<Unit>()
    fun returned() { done.complete(Unit) }
    override fun closeAcknowledged(): CompletionStage<Unit> = done
}

/** Fixed synchronous provider lifetime, not a claim of universal provider-memory wipe.
 * Inspected Conscrypt uses the supplied encoded-key alias through init AND doFinal. */
internal class PinCipherCall : AcknowledgedCloseable {
    private val done = CompletableFuture<Unit>()
    private var cipher: Cipher? = null
    private var key: BorrowedPrimaryPinKey? = null
    private var parameters: GCMParameterSpec? = null
    fun bind(value: Cipher) { check(cipher == null); cipher = value }
    fun init(context: PrimaryPinProducer, wrapping: ByteArray, nonce: ByteArray) {
        context.check()
        key = BorrowedPrimaryPinKey(wrapping)
        parameters = GCMParameterSpec(128, nonce)
        checkNotNull(cipher).init(Cipher.DECRYPT_MODE, key, parameters)
    }
    fun decrypt(ciphertext: ByteArray, destination: ByteArray) {
        try { check(checkNotNull(cipher).doFinal(ciphertext, 0, ciphertext.size, destination, 0) == 32) { "Invalid authentication key" } }
        catch (_: AEADBadTagException) { throw InvalidPinException() }
    }
    fun returned() {
        key?.seal(); key = null; parameters = null; cipher = null
        done.complete(Unit)
    }
    override fun closeAcknowledged(): CompletionStage<Unit> = done
}

/** Private standard RAW key: no SecretKeySpec clone, serialization or reusable export. */
private class BorrowedPrimaryPinKey(@Transient private var bytes: ByteArray?) : SecretKey {
    @Transient private var thread: Thread? = Thread.currentThread()
    override fun getAlgorithm(): String = "AES"
    override fun getFormat(): String = "RAW"
    override fun getEncoded(): ByteArray {
        check(thread === Thread.currentThread()) { "Original PIN key borrow unavailable" }
        return checkNotNull(bytes) { "Original PIN key borrow ended" }
    }
    fun seal() { bytes = null; thread = null }
    private fun writeObject(output: java.io.ObjectOutputStream): Unit = throw java.io.NotSerializableException("Borrowed PIN key")
}
