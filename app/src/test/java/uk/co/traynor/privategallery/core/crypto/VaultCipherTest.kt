package uk.co.traynor.privategallery.core.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class VaultCipherTest {
  @Test fun `large payload decrypt uses bulk reads and preserves authentication`() {
    val key = ByteArray(32) { it.toByte() }
    val original = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
    val encrypted = ByteArrayOutputStream()
    val aad = "bulk-fixture".encodeToByteArray()
    val header = VaultCipher.encrypt(ByteArrayInputStream(original), encrypted, key, aad)
    var reads = 0
    val input = object : ByteArrayInputStream(encrypted.toByteArray()) {
      override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        reads++; return super.read(buffer, offset, length)
      }
    }
    val output = ByteArrayOutputStream()
    VaultCipher.decrypt(input, output, key, aad, header)
    assertArrayEquals(original, output.toByteArray())
    org.junit.Assert.assertTrue("Small cipher reads regress large-video startup: $reads", reads < 100)
    val damaged = encrypted.toByteArray().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
    org.junit.Assert.assertThrows(Exception::class.java) {
      VaultCipher.decrypt(ByteArrayInputStream(damaged), ByteArrayOutputStream(), key, aad, header)
    }
  }

  @Test fun `encrypt then decrypt preserves bytes`() {
    val key = ByteArray(32).also(SecureRandom()::nextBytes)
    val ciphertext = ByteArrayOutputStream()
    val header = VaultCipher.encrypt(ByteArrayInputStream("private media".encodeToByteArray()), ciphertext, key, "item-1".encodeToByteArray())
    val plaintext = ByteArrayOutputStream()
    VaultCipher.decrypt(ByteArrayInputStream(ciphertext.toByteArray()), plaintext, key, "item-1".encodeToByteArray(), header)
    assertArrayEquals("private media".encodeToByteArray(), plaintext.toByteArray())
  }
}
