package uk.co.traynor.privategallery.core.domain

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** F1-r2 whole records only. Each invocation creates a fresh salt and nonce. */
object F1Record {
  const val HEADER_LENGTH = 156
  private val magic = "PGFUTR01".toByteArray(Charsets.US_ASCII)
  private val label = "private-gallery:future:key:v1".toByteArray(Charsets.US_ASCII)

  fun encrypt(master: ByteArray, context: F1Context, plaintext: ByteArray): ByteArray {
    f1Check(master.size == 32)
    f1Check(plaintext.size.toLong() <= maximumBodyLength(context.purpose))
    val salt = F1Crypto.random(32)
    val nonce = F1Crypto.random(12)
    val header = wholeHeader(context, salt, nonce, plaintext.size.toLong())
    val key = deriveKey(master, salt, context)
    return try { header + F1Crypto.aead(Cipher.ENCRYPT_MODE, key, nonce, header, plaintext) }
    finally { key.fill(0) }
  }

  fun decrypt(master: ByteArray, expected: F1Context, encoded: ByteArray): ByteArray {
    f1Check(master.size == 32)
    // Bound the snapshot before copying; parse and authenticate that same immutable snapshot.
    f1Check(encoded.size >= HEADER_LENGTH + 16 && encoded.size.toLong() <= 64L * 1024 * 1024 + HEADER_LENGTH + 16)
    val snapshot = encoded.copyOf()
    val header = snapshot.copyOfRange(0, HEADER_LENGTH)
    val input = ByteBuffer.wrap(header)
    f1Check(header.copyOfRange(0, 8).contentEquals(magic))
    if (F1Crypto.u16(input, 8) != 1) throw F1Exception(F1Failure.UNSUPPORTED)
    f1Check(F1Crypto.u16(input, 10) == HEADER_LENGTH && F1Crypto.u16(input, 12) == 1)
    val purpose = F1Crypto.u16(input, 14)
    val maximum = maximumBodyLength(purpose)
    f1Check(purpose == expected.purpose)
    f1Check(expected.identity.matches(header.copyOfRange(16, 32), header.copyOfRange(32, 48)))
    f1Check(expected.matchesObject(header.copyOfRange(48, 64)))
    val generation = input.getLong(64)
    f1Check(generation > 0 && generation == expected.generation)
    val length = input.getLong(116)
    val encryptedLength = input.getLong(124)
    val total = input.getLong(132)
    f1Check(length in 0..maximum && encryptedLength == length + 16 && total == length)
    f1Check(snapshot.size.toLong() == HEADER_LENGTH.toLong() + encryptedLength)
    f1Check((140..152 step 4).all { input.getInt(it) == 0 })
    val key = deriveKey(master, header.copyOfRange(72, 104), expected)
    return try {
      F1Crypto.aead(Cipher.DECRYPT_MODE, key, header.copyOfRange(104, 116), header, snapshot, HEADER_LENGTH, encryptedLength.toInt())
    } finally { key.fill(0) }
  }

  internal fun maximumBodyLength(purpose: Int): Long = when (purpose) {
    1, 8 -> 16L * 1024 * 1024
    5, 7, 9 -> 64L * 1024
    2, 3, 4, 6 -> 64L * 1024 * 1024
    else -> throw F1Exception(F1Failure.CORRUPT)
  }

  internal fun keyInfo(context: F1Context): ByteArray = ByteBuffer.allocate(93)
    .putShort(label.size.toShort()).put(label).putShort(1)
    .put(context.identity.container).put(context.identity.master)
    .putShort(context.purpose.toShort()).put(context.objectId).putLong(context.generation)
    .putShort(1).array()

  internal fun deriveKey(master: ByteArray, salt: ByteArray, context: F1Context): ByteArray {
    f1Check(master.size == 32 && salt.size == 32)
    return F1Crypto.hkdf(master, salt, keyInfo(context))
  }

  internal fun wholeHeader(context: F1Context, salt: ByteArray, nonce: ByteArray, length: Long): ByteArray {
    f1Check(salt.size == 32 && nonce.size == 12 && length in 0..maximumBodyLength(context.purpose))
    return ByteBuffer.allocate(HEADER_LENGTH).put(magic).putShort(1).putShort(HEADER_LENGTH.toShort())
      .putShort(1).putShort(context.purpose.toShort()).put(context.identity.container)
      .put(context.identity.master).put(context.objectId).putLong(context.generation)
      .put(salt).put(nonce).putLong(length).putLong(length + 16).putLong(length)
      .putInt(0).putInt(0).putInt(0).putInt(0).array()
  }
}

/** Shared standard primitives; no legacy crypto dependency and no deterministic production RNG. */
internal object F1Crypto {
  fun random(length: Int): ByteArray = try { ByteArray(length).also(SecureRandom()::nextBytes) }
    catch (_: RuntimeException) { throw F1Exception(F1Failure.UNAVAILABLE) }

  fun u16(input: ByteBuffer, offset: Int): Int = input.getShort(offset).toInt() and 0xffff

  fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray): ByteArray {
    val copy = ikm.copyOf()
    val prk = try { extract(salt, copy) } finally { copy.fill(0) }
    return try { expand(prk, info, 32) } finally { prk.fill(0) }
  }

  fun extract(salt: ByteArray, ikm: ByteArray): ByteArray = hmac(salt, ikm)

  fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
    f1Check(length in 1..255 * 32)
    val result = ByteArray(length)
    var previous = byteArrayOf()
    var written = 0
    var counter = 1
    try {
      while (written < length) {
        val input = previous + info + counter.toByte()
        val next = try { hmac(prk, input) } finally { input.fill(0) }
        previous.fill(0); previous = next
        val count = minOf(next.size, length - written)
        next.copyInto(result, written, 0, count)
        written += count; counter++
      }
      return result
    } catch (failure: Throwable) {
      result.fill(0)
      throw failure
    } finally { previous.fill(0) }
  }

  private fun hmac(key: ByteArray, input: ByteArray): ByteArray = try {
    Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(input) }
  } catch (_: GeneralSecurityException) { throw F1Exception(F1Failure.UNAVAILABLE) }

  fun aead(mode: Int, key: ByteArray, nonce: ByteArray, header: ByteArray, body: ByteArray, offset: Int = 0, length: Int = body.size): ByteArray = try {
    Cipher.getInstance("AES/GCM/NoPadding").run {
      init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
      updateAAD(header)
      doFinal(body, offset, length)
    }
  } catch (_: GeneralSecurityException) { throw F1Exception(if (mode == Cipher.DECRYPT_MODE) F1Failure.CORRUPT else F1Failure.UNAVAILABLE) }
}
