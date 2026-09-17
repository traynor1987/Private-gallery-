package uk.co.traynor.privategallery.core.crypto

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
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

  fun encrypt(input: InputStream, output: OutputStream, key: ByteArray, aad: ByteArray): EncryptionHeader {
    require(key.size == 32) { "Vault key must be 256 bits" }
    val nonce = ByteArray(EncryptionHeader.NONCE_BYTES).also(SecureRandom()::nextBytes)
    cipher(Cipher.ENCRYPT_MODE, key, nonce, aad).let { crypto ->
      CipherOutputStream(output, crypto).use { encrypted -> input.copyTo(encrypted, BUFFER_BYTES) }
    }
    return EncryptionHeader(nonce)
  }

  fun decrypt(input: InputStream, output: OutputStream, key: ByteArray, aad: ByteArray, header: EncryptionHeader) {
    require(key.size == 32) { "Vault key must be 256 bits" }
    CipherInputStream(input, cipher(Cipher.DECRYPT_MODE, key, header.nonce, aad)).use { decrypted ->
      decrypted.copyTo(output, BUFFER_BYTES)
    }
  }

  private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray): Cipher =
    Cipher.getInstance("AES/GCM/NoPadding").apply {
      init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
      updateAAD(aad)
    }
}
