package uk.co.traynor.privategallery.core.hidden

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hidden-only credentials. No Primary preferences, repository or backup path is consulted.
 * Not exposed to the UI until credential-difference and lifecycle integration tests pass.
 * All calls must run off the main thread. Missing/corrupt records never trigger replacement.
 */
internal class HiddenCredentialStore(context: Context, namespace: String = "default") {
    init { require(namespace.matches(Regex("[a-zA-Z0-9-]{1,64}"))) }
    private val root = File(context.noBackupFilesDir, "hidden-v1-$namespace")
    private val file = File(root, "credential")
    private val atomic = AtomicFile(file)
    private val alias = "${context.packageName}.hidden.v1.$namespace"

    val isConfigured: Boolean get() = synchronized(gate) { hasRecord() }

    /** Returns a new random Hidden key. Caller owns it and must hand it to HiddenSession or wipe it. */
    fun create(pin: CharArray): ByteArray = synchronized(gate) {
        val key = ByteArray(32)
        var inner: ByteArray? = null
        try {
            check(!hasRecord()) { "Hidden credentials already exist or need recovery" }
            SecureRandom().nextBytes(key)
            inner = HiddenPinEnvelope.wrap(pin, key)
            val secret = deviceKey(create = true)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secret)
            check(cipher.iv.size == 12)
            cipher.updateAAD(OUTER_HEADER)
            val sealed = OUTER_HEADER + cipher.iv + cipher.doFinal(inner)
            check(sealed.size == OUTER_BYTES)
            val verified = decrypt(sealed)
            try { check(MessageDigest.isEqual(inner, verified)) { "Credential verification failed" } }
            finally { verified.fill(0) }
            check(root.isDirectory || root.mkdirs()) { "Hidden storage unavailable" }
            val output = atomic.startWrite()
            try {
                output.write(sealed)
                atomic.finishWrite(output)
            } catch (failure: Throwable) {
                atomic.failWrite(output)
                throw failure
            }
            check(MessageDigest.isEqual(sealed, readRecord())) { "Hidden credential write did not persist" }
            key
        } catch (failure: Throwable) {
            key.fill(0)
            throw failure
        } finally { inner?.fill(0); pin.fill('\u0000') }
    }

    fun unlock(pin: CharArray): ByteArray = synchronized(gate) {
        var inner: ByteArray? = null
        try {
            check(hasRecord()) { "Hidden credentials are not configured" }
            inner = decrypt(readRecord())
            HiddenPinEnvelope.unwrap(pin, inner)
        } finally { inner?.fill(0); pin.fill('\u0000') }
    }

    /** Serializes credential comparison with all Primary credential writes. */
    fun createDifferentFromPrimary(pin: CharArray, primaryMatches: (CharArray) -> Boolean): ByteArray = synchronized(gate) {
        try {
            val proposed = pin.copyOf()
            try { check(!primaryMatches(proposed)) { "Use a different Hidden PIN" } }
            finally { proposed.fill('\u0000') }
            create(pin)
        } finally { pin.fill('\u0000') }
    }

    /** Consumes input. Corrupt/missing device credentials fail closed rather than assuming a mismatch. */
    fun pinMatches(pin: CharArray): Boolean = synchronized(gate) {
        try {
            if (!hasRecord() || pin.size !in 6..12 || pin.any { it !in '0'..'9' }) return@synchronized false
            val key = try { unlock(pin) } catch (_: HiddenPinEnvelope.InvalidPin) { return@synchronized false }
            key.fill(0)
            true
        } finally { pin.fill('\u0000') }
    }

    private fun hasRecord(): Boolean = file.exists() || File(file.path + ".bak").exists() || File(file.path + ".new").exists()

    private fun readRecord(): ByteArray = atomic.openRead().use { input ->
        val bounded = ByteArray(OUTER_BYTES + 1)
        var count = 0
        while (count < bounded.size) {
            val read = input.read(bounded, count, bounded.size - count)
            if (read < 0) break
            check(read > 0) { "Credential read stalled" }
            count += read
        }
        check(count == OUTER_BYTES) { "Invalid Hidden credential length" }
        bounded.copyOf(count)
    }

    private fun decrypt(record: ByteArray): ByteArray {
        check(record.size == OUTER_BYTES && record.copyOfRange(0, 4).contentEquals(OUTER_HEADER)) { "Invalid Hidden credential format" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deviceKey(create = false), GCMParameterSpec(128, record.copyOfRange(4, 16)))
        cipher.updateAAD(OUTER_HEADER)
        return cipher.doFinal(record, 16, record.size - 16)
    }

    private fun deviceKey(create: Boolean): SecretKey {
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keys.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create && !hasRecord()) { "Hidden device key unavailable; credentials retained" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()
    }

    companion object {
        internal val gate = Any()
        private val OUTER_HEADER = byteArrayOf('P'.code.toByte(), 'G'.code.toByte(), 'H'.code.toByte(), 1)
        private const val OUTER_BYTES = 4 + 12 + HiddenPinEnvelope.RECORD_BYTES + 16
    }
}
