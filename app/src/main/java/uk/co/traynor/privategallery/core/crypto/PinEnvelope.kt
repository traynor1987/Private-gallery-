package uk.co.traynor.privategallery.core.crypto

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.SCrypt

data class PinWrappedKey(val salt: ByteArray, val nonce: ByteArray, val ciphertext: ByteArray)
class InvalidPinException : SecurityException("PIN did not unlock the vault")

object PinEnvelope {
  private const val N = 1 shl 15
  private const val R = 8
  private const val P = 1
  private const val TAG_BITS = 128

  fun create(pin: CharArray, vdek: ByteArray): PinWrappedKey {
    require(vdek.size == 32)
    val salt = ByteArray(16).also(SecureRandom()::nextBytes)
    val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
    return try {
      val encrypted = cipher(Cipher.ENCRYPT_MODE, derive(pin, salt), nonce).doFinal(vdek)
      PinWrappedKey(salt, nonce, encrypted)
    } finally { pin.fill('\u0000') }
  }

  fun unwrap(pin: CharArray, envelope: PinWrappedKey): ByteArray = try {
    cipher(Cipher.DECRYPT_MODE, derive(pin, envelope.salt), envelope.nonce).doFinal(envelope.ciphertext)
  } catch (_: AEADBadTagException) { throw InvalidPinException() } finally { pin.fill('\u0000') }

  fun changePin(oldPin: CharArray, newPin: CharArray, envelope: PinWrappedKey): PinWrappedKey {
    val vdek = unwrap(oldPin, envelope)
    return try { create(newPin, vdek) } finally { vdek.fill(0) }
  }

  private fun derive(pin: CharArray, salt: ByteArray): ByteArray =
    SCrypt.generate(pin.concatToString().encodeToByteArray(), salt, N, R, P, 32)
  private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray): Cipher =
    Cipher.getInstance("AES/GCM/NoPadding").apply { init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce)) }
}
