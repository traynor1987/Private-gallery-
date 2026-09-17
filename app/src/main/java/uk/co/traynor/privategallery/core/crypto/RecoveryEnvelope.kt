package uk.co.traynor.privategallery.core.crypto

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.SCrypt

/**
 * A third, independent envelope around the VDEK. The recovery key exists only
 * while it is displayed or entered by its owner; this envelope never stores it.
 */
data class RecoveryWrappedKey(val salt: ByteArray, val nonce: ByteArray, val ciphertext: ByteArray)
class InvalidRecoveryKeyException : SecurityException("Recovery key did not unlock the vault")

object RecoveryKey {
    private const val BYTES = 32

    fun generate(): CharArray = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(BYTES).also(SecureRandom()::nextBytes))
        .toCharArray()

    /** Whitespace is a presentation separator; '-' is valid URL-safe Base64 key material. */
    fun display(value: CharArray): String = value.concatToString().chunked(5).joinToString(" ")

    fun normalise(value: CharArray): CharArray = value.concatToString()
        .filterNot(Char::isWhitespace)
        .toCharArray()
}

object RecoveryEnvelope {
    private const val N = 1 shl 15
    private const val R = 8
    private const val P = 1
    private const val TAG_BITS = 128

    fun create(recoveryKey: CharArray, vdek: ByteArray): RecoveryWrappedKey {
        require(vdek.size == 32)
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        return try {
            val wrappingKey = derive(recoveryKey, salt)
            val ciphertext = try {
                cipher(Cipher.ENCRYPT_MODE, wrappingKey, nonce).doFinal(vdek)
            } finally {
                wrappingKey.fill(0)
            }
            RecoveryWrappedKey(salt, nonce, ciphertext)
        } finally {
            recoveryKey.fill('\u0000')
        }
    }

    fun unwrap(recoveryKey: CharArray, envelope: RecoveryWrappedKey): ByteArray = try {
        val normalised = RecoveryKey.normalise(recoveryKey)
        try {
            val wrappingKey = derive(normalised, envelope.salt)
            try {
                cipher(Cipher.DECRYPT_MODE, wrappingKey, envelope.nonce).doFinal(envelope.ciphertext)
            } finally {
                wrappingKey.fill(0)
            }
        } finally {
            normalised.fill('\u0000')
        }
    } catch (_: AEADBadTagException) {
        throw InvalidRecoveryKeyException()
    } finally {
        recoveryKey.fill('\u0000')
    }

    private fun derive(key: CharArray, salt: ByteArray): ByteArray {
        val keyBytes = key.concatToString().encodeToByteArray()
        return try {
            SCrypt.generate(keyBytes, salt, N, R, P, 32)
        } finally {
            keyBytes.fill(0)
        }
    }

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        }
}
