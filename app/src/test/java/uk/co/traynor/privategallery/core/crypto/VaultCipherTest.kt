package uk.co.traynor.privategallery.core.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class VaultCipherTest {
  @Test fun `encrypt then decrypt preserves bytes`() {
    val key = ByteArray(32).also(SecureRandom()::nextBytes)
    val ciphertext = ByteArrayOutputStream()
    val header = VaultCipher.encrypt(ByteArrayInputStream("private media".encodeToByteArray()), ciphertext, key, "item-1".encodeToByteArray())
    val plaintext = ByteArrayOutputStream()
    VaultCipher.decrypt(ByteArrayInputStream(ciphertext.toByteArray()), plaintext, key, "item-1".encodeToByteArray(), header)
    assertArrayEquals("private media".encodeToByteArray(), plaintext.toByteArray())
  }
}
