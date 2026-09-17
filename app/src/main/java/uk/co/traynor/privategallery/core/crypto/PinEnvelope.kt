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
      val wrappingKey = derive(pin, salt)
      val encrypted = try {
        cipher(Cipher.ENCRYPT_MODE, wrappingKey, nonce).doFinal(vdek)
      } finally {
        wrappingKey.fill(0)
      }
      PinWrappedKey(salt, nonce, encrypted)
    } finally { pin.fill('\u0000') }
  }

  fun unwrap(pin: CharArray, envelope: PinWrappedKey): ByteArray = try {
    val wrappingKey = derive(pin, envelope.salt)
    try {
      cipher(Cipher.DECRYPT_MODE, wrappingKey, envelope.nonce).doFinal(envelope.ciphertext)
    } finally {
      wrappingKey.fill(0)
    }
  } catch (_: AEADBadTagException) { throw InvalidPinException() } finally { pin.fill('\u0000') }

  fun changePin(oldPin: CharArray, newPin: CharArray, envelope: PinWrappedKey): PinWrappedKey {
    val vdek = unwrap(oldPin, envelope)
    return try { create(newPin, vdek) } finally { vdek.fill(0) }
  }

  private fun derive(pin: CharArray, salt: ByteArray): ByteArray {
    require(pin.all { it.code <= 0x7f }) { "PIN contains unsupported characters" }
    val pinBytes = ByteArray(pin.size) { pin[it].code.toByte() }
    return try {
      SCrypt.generate(pinBytes, salt, N, R, P, 32)
    } finally {
      pinBytes.fill(0)
    }
  }
  private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray): Cipher =
    Cipher.getInstance("AES/GCM/NoPadding").apply { init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce)) }
}
