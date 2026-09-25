package uk.co.traynor.privategallery.core.crypto

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptionHeader(val nonce: ByteArray) {
  init { require(nonce.size == NONCE_BYTES) }
  companion object { const val NONCE_BYTES = 12 }
}

/** Streaming AES-256-GCM; ciphertext includes the authentication tag. */
object VaultCipher {
  private const val TAG_BITS = 128
  private const val BUFFER_BYTES = 64 * 1024

  fun encrypt(input: InputStream, output: OutputStream, key: ByteArray, aad: ByteArray): EncryptionHeader =
    encrypt(input, output, key, aad, ByteArray(EncryptionHeader.NONCE_BYTES).also(SecureRandom()::nextBytes))

  fun encrypt(input: InputStream, output: OutputStream, key: ByteArray, aad: ByteArray, nonce: ByteArray): EncryptionHeader {
    require(key.size == 32) { "Vault key must be 256 bits" }
    require(nonce.size == EncryptionHeader.NONCE_BYTES) { "Invalid AES-GCM nonce" }
    cipher(Cipher.ENCRYPT_MODE, key, nonce, aad).let { crypto ->
      // CipherOutputStream.close() emits the GCM tag.  It must not close the
      // caller's stream: the payload store still fsyncs that descriptor before
      // promoting ciphertext from staging.
      CipherOutputStream(NonClosingOutputStream(output), crypto).use { encrypted ->
        input.copyTo(encrypted, BUFFER_BYTES)
      }
    }
    return EncryptionHeader(nonce)
  }

  fun decrypt(input: InputStream, output: OutputStream, key: ByteArray, aad: ByteArray, header: EncryptionHeader) {
    require(key.size == 32) { "Vault key must be 256 bits" }
    val crypto = cipher(Cipher.DECRYPT_MODE, key, header.nonce, aad)
    val buffer = ByteArray(BUFFER_BYTES)
    fun writeAndClear(bytes: ByteArray?) {
      if (bytes == null) return
      try { output.write(bytes) } finally { bytes.fill(0) }
    }
    try {
      input.use {
        while (true) {
          val count = it.read(buffer)
          if (count < 0) break
          if (count > 0) writeAndClear(crypto.update(buffer, 0, count))
        }
        // Do not suppress authentication failures. Callers must not publish their
        // private output until this completes successfully, and wipe on failure.
        writeAndClear(crypto.doFinal())
      }
    } finally { buffer.fill(0) }
  }

  private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray): Cipher =
    Cipher.getInstance("AES/GCM/NoPadding").apply {
      init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
      updateAAD(aad)
    }

  private class NonClosingOutputStream(private val delegate: OutputStream) : OutputStream() {
    override fun write(value: Int) = delegate.write(value)
    override fun write(buffer: ByteArray, offset: Int, length: Int) = delegate.write(buffer, offset, length)
    override fun flush() = delegate.flush()
    override fun close() = delegate.flush()
  }
}
